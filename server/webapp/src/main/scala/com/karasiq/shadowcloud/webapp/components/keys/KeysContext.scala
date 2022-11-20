package com.karasiq.shadowcloud.webapp.components.keys

import rx.{Ctx, Rx}

import com.karasiq.shadowcloud.model.keys.KeyChain
import com.karasiq.shadowcloud.webapp.context.AppContext
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext
import com.karasiq.shadowcloud.webapp.utils.RxWithUpdate

trait KeysContext {
  def keys: Rx[KeyChain]
  def updateAll(): Unit
}

object KeysContext {
  def apply()(implicit appContext: AppContext, ctxOwner: Ctx.Owner): KeysContext = new KeysContext {
    private[this] val _keys = RxWithUpdate(KeyChain.empty)(_ ⇒ appContext.api.getKeys())
    def keys: Rx[KeyChain]  = _keys.toRx
    def updateAll(): Unit   = _keys.update()
  }
}
