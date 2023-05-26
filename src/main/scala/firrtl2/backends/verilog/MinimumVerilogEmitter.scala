package firrtl2

import firrtl2.stage.TransformManager

class MinimumVerilogEmitter extends VerilogEmitter with Emitter {

  override def prerequisites = firrtl2.stage.Forms.AssertsRemoved ++
    firrtl2.stage.Forms.LowFormMinimumOptimized

  override def transforms =
    new TransformManager(firrtl2.stage.Forms.VerilogMinimumOptimized, prerequisites).flattenedTransformOrder

}
