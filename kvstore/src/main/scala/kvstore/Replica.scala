package kvstore

import akka.actor.{ OneForOneStrategy, Props, ActorRef, Actor }
import kvstore.Arbiter._
import scala.collection.immutable.Queue
import akka.actor.SupervisorStrategy.Restart
import scala.annotation.tailrec
import akka.pattern.{ ask, pipe }
import akka.actor.Terminated
import scala.concurrent.duration._
import akka.actor.PoisonPill
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy
import akka.util.Timeout

object Replica {
  sealed trait Operation {
    def key: String
    def id: Long
  }
  case class Insert(key: String, value: String, id: Long) extends Operation
  case class Remove(key: String, id: Long) extends Operation
  case class Get(key: String, id: Long) extends Operation

  sealed trait OperationReply
  case class OperationAck(id: Long) extends OperationReply
  case class OperationFailed(id: Long) extends OperationReply
  case class GetResult(key: String, valueOption: Option[String], id: Long) extends OperationReply

  def props(arbiter: ActorRef, persistenceProps: Props): Props = Props(new Replica(arbiter, persistenceProps))
}

class Replica(val arbiter: ActorRef, persistenceProps: Props) extends Actor {
  import Replica._
  import Replicator._
  import Persistence._
  import context.dispatcher

  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */

  arbiter ! Join

  var kv = Map.empty[String, String]
  // a map from secondary replicas to replicators
  var secondaries = Map.empty[ActorRef, ActorRef]
  // the current set of replicators
  var replicators = Set.empty[ActorRef]
  // id of request to actor who made request
  var senders = Map.empty[Long, ActorRef]

  var expectedSeq = 0

  def receive = {
    case JoinedPrimary   => context.become(leader)
    case JoinedSecondary => context.become(replica)
  }

  def get(key:String, id:Long) {
    val valueOption = kv.get(key)
    sender ! GetResult(key, valueOption, id)
  }
  
  def insert(key:String, value:String, id:Long) {
    kv = kv + (key -> value)
    sender ! OperationAck(id)
    replicators.foreach { replicator =>
      replicator ! Replicate(key, Some(value), id)
    }
  }
  
  def remove(key:String, id:Long) {
    kv = kv - key
    sender ! OperationAck(id)
    replicators.foreach { replicator =>
      replicator ! Replicate(key, None, id)
    }
  }

  def allocateReplicas(replicas:Set[ActorRef]) {
    val knownReplicas: Set[ActorRef] = secondaries.keys.toSet
    val toAdd = (replicas - self) &~ knownReplicas
    val toRemove = (knownReplicas - self) &~ replicas

    toAdd.foreach { replica =>
      val replicator = context.actorOf(Replicator.props(replica), s"$replica-replicator")
      replicators = replicators + replicator
      secondaries = secondaries + (replica -> replicator)
      kv.zip(Stream.from(0)).foreach { case ((k, v), id) =>
        replicator ! Replicate(k, Some(v), id * -1)
      }
    }

    toRemove.foreach { replica =>
      val replicator = secondaries(replica)
      replicator ! PoisonPill
      secondaries = secondaries - replica
      replicators = replicators - replica
    }
  }

  def persist(seq:Long) {
    senders = senders + (seq -> sender)
  }

  def snapshot(sn:Snapshot) {
    val Snapshot(key, valueOption, seq) = sn
    if (seq < expectedSeq) {
      sender ! SnapshotAck(key, seq)
    }
    if (seq == expectedSeq) {
      valueOption match {
        case Some(v) => kv = kv + (key -> v)
        case None => kv = kv - key
      }
      expectedSeq += 1
      persist(seq)
    }
  }

  /* TODO Behavior for  the leader role. */
  val leader: Receive = {
    case Get(key, id) => get(key, id)
    case Insert(key, value, id) => insert(key, value, id)
    case Remove(key, id) => remove(key, id)
    case Replicas(actors) => allocateReplicas(actors)
  }

  /* TODO Behavior for the replica role. */
  val replica: Receive = {
    case Get(key, id) => get(key, id)
    case sn:Snapshot => snapshot(sn)
    case Persisted(key, id) => {
      senders(id) ! SnapshotAck(key, id)
      senders = senders - id
    }

  }

}
