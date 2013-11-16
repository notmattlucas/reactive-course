package simulations

import org.scalatest.FunSuite

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class CircuitSuite extends CircuitSimulator with FunSuite {
  val InverterDelay = 1
  val AndGateDelay = 3
  val OrGateDelay = 5
  
  test("andGate example") {
    val in1, in2, out = new Wire
    andGate(in1, in2, out)
    in1.setSignal(false)
    in2.setSignal(false)
    run
    
    assert(out.getSignal === false, "and 1")

    in1.setSignal(true)
    run
    
    assert(out.getSignal === false, "and 2")

    in2.setSignal(true)
    run
    
    assert(out.getSignal === true, "and 3")
  }

  private def assertOr(or:(Wire, Wire, Wire) => Unit, a:Boolean, b:Boolean, c:Boolean) {
    val in1, in2, out = new Wire
    or(in1, in2, out)
    in1.setSignal(a)
    in2.setSignal(b)
    run
    assert(out.getSignal === c)
  }

  test("orGate (0 | 0) => 0") {
    assertOr(orGate, false, false, false)
  }

  test("orGate (1 | 0) => 1") {
    assertOr(orGate, true, false, true)
  }

  test("orGate (0 | 1) => 1") {
    assertOr(orGate, false, true, true)
  }

  test("orGate (1 | 1) => 1") {
    assertOr(orGate, true, true, true)
  }

  test("orGate2 (0 | 0) => 0") {
    assertOr(orGate2, false, false, false)
  }

  test("orGate2 (1 | 0) => 1") {
    assertOr(orGate2, true, false, true)
  }

  test("orGate2 (0 | 1) => 1") {
    assertOr(orGate2, false, true, true)
  }

  test("orGate2 (1 | 1) => 1") {
    assertOr(orGate2, true, true, true)
  }

  //
  // to complete with tests for orGate, demux, ...
  //

}
