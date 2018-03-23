; See LICENSE for license details.
circuit ZeroWidthMem :
  module ZeroWidthMem :
    input clock : Clock
    input reset : UInt<1>
    input waddr : UInt<4>
    input in   : {0: UInt<10>, 1: UInt<0>}
    input raddr : UInt<4>
    output out : {0: UInt<10>, 1: UInt<0>}

    cmem ram : {0: UInt<10>, 1: UInt<0>}[16]
    infer mport ramin = ram[waddr], clock
    infer mport ramout = ram[raddr], clock

    ramin.0 <= in.0
    ramin.1 <= in.1
    out <= ramout

    wire foo : UInt<32>
    foo <= UInt<32>("hdeadbeef")

    when not(reset) :
      when eq(foo, UInt<32>("hdeadbeef")) :
        stop(clock, UInt(1), 0) ; Success !
      else :
        printf(clock, UInt(1), "Assertion failed!\n")
        stop(clock, UInt(1), 1) ; Failure!
