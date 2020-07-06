package com.karasiq.shadowcloud.webapp.components.keys

import akka.util.ByteString
import com.karasiq.bootstrap.Bootstrap.default._
import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.model.keys.KeyProps.RegionSet
import com.karasiq.shadowcloud.model.keys.KeySet
import com.karasiq.shadowcloud.webapp.components.common.AppComponents
import com.karasiq.shadowcloud.webapp.components.region.RegionContext
import com.karasiq.shadowcloud.webapp.context.AppContext
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext
import com.karasiq.shadowcloud.webapp.utils.ExportUtils
import rx.{Rx, Var}
import scalaTags.all._

object KeysView {
  def apply()(implicit context: AppContext, kc: KeysContext, rc: RegionContext): KeysView = {
    new KeysView()
  }
}

class KeysView()(implicit context: AppContext, kc: KeysContext, rc: RegionContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    val heading = Seq[ModifierT](
      context.locale.keyId,
      context.locale.keyEncAlgorithm,
      context.locale.keySignAlgorithm
    )

    val rows = kc.keys.map { keyChain ⇒
      val (activeKeys, disabledKeys) = keyChain.keys.partition(kp ⇒ kp.forEncryption || kp.forDecryption)

      val activeRows = activeKeys.map { kp ⇒
        val key = kp.key
        val style = if (kp.regionSet.isEmpty) TableRowStyle.active else TableRowStyle.info
        TableRow(Seq(key.id.toString, key.encryption.method.algorithm, key.signing.method.algorithm), style,
          onclick := Callback.onClick(_ ⇒ showKeyDialog(key, kp.regionSet, kp.forEncryption, kp.forDecryption)))
      }

      val nonActiveRows = disabledKeys.map { kp ⇒
        val key = kp.key
        TableRow(Seq(key.id.toString, key.encryption.method.algorithm, key.signing.method.algorithm), textDecoration.`line-through`,
          onclick := Callback.onClick(_ ⇒ showKeyDialog(key, kp.regionSet, kp.forEncryption, kp.forDecryption)))
      }

      activeRows ++ nonActiveRows
    }

    div(
      GridSystem.mkRow(renderAddButtons()),
      GridSystem.mkRow(PagedTable(Var(heading), rows))
    )
  }

  private[this] def renderAddButtons(): TagT = {
    def showGenerateDialog(): Unit = {
      def doGenerate(propsString: String): Unit = {
        val props = SerializedProps(SerializedProps.DefaultFormat, ByteString(propsString))
        context.api.generateKey(props = props).foreach { key ⇒
          showExportDialog(key)
          kc.updateAll()
        }
      }

      val propsPlaceholder =
        """|# Example custom configuration (optional)
           |encryption.algorithm = "X25519+XSalsa20/Poly1305"
           |signing {
           |  algorithm = Ed25519
           |  hashing.algorithm = Blake2b
           |}""".stripMargin

      val propsRx = Var("")
      Modal()
        .withTitle(context.locale.generateKey)
        .withBody(Form(FormInput.textArea(context.locale.config, placeholder := propsPlaceholder, rows := 20, AppComponents.tabOverride, propsRx.reactiveInput)))
        .withButtons(
          AppComponents.modalSubmit(onclick := Callback.onClick(_ ⇒ doGenerate(propsRx.now))),
          AppComponents.modalClose()
        )
        .show()
    }

    def showImportDialog(): Unit = {
      AppComponents.importDialog(context.locale.importKey) { result =>
        val key = ExportUtils.decodeKey(result)
        context.api.addKey(key).foreach(_ ⇒ kc.updateAll())
      }.show()
    }

    val generateButton = Button(ButtonStyle.success, block = true)(
      context.locale.generateKey,
      onclick := Callback.onClick(_ ⇒ showGenerateDialog())
    )

    val importButton = Button(ButtonStyle.primary, block = true)(
      context.locale.importKey,
      onclick := Callback.onClick(_ ⇒ showImportDialog())
    )

    GridSystem.row(
      GridSystem.col.md(6)(generateButton),
      GridSystem.col.md(6)(importButton)
    )
  }

  private[this] def showExportDialog(key: KeySet): Unit = {
    AppComponents.exportDialog(context.locale.exportKey, s"${key.id}.json", ExportUtils.encodeKey(key))
      .show(backdrop = false)
  }

  private[this] def showKeyDialog(key: KeySet, regionSet: RegionSet, forEncryption: Boolean, forDecryption: Boolean): Unit = {
    def updatePermissions(regionSet: RegionSet, forEncryption: Boolean, forDecryption: Boolean): Unit = {
      context.api.modifyKey(key.id, regionSet, forEncryption, forDecryption).foreach(_ ⇒ kc.updateAll())
    }

    def renderPermissions() = {
      val forEncryptionRx = Var(forEncryption)
      val forDecryptionRx = Var(forDecryption)
      val allIds = rc.regions.now.regions.keys.toVector
      val (idSelect, idSelectRendered) = AppComponents.idSelect(context.locale.regions, allIds, regionSet.toVector)

      Rx((forEncryptionRx(), forDecryptionRx(), idSelect.selected()))
        .triggerLater(updatePermissions(idSelect.selected.now.toSet, forEncryptionRx.now, forDecryptionRx.now))
      
      Form(
        FormInput.checkbox(context.locale.keyForEncryption, forEncryptionRx.reactiveInput),
        FormInput.checkbox(context.locale.keyForDecryption, forDecryptionRx.reactiveInput),
        idSelectRendered
      )
    }

    def renderKeyInfo() = {
      Bootstrap.well(key.toString)
    }

    Modal()
      .withTitle(key.id.toString)
      .withBody(
        GridSystem.mkRow(renderKeyInfo()),
        GridSystem.mkRow(renderPermissions())
      )
      .withButtons(
        Button(ButtonStyle.warning)(context.locale.exportKey, onclick := Callback.onClick(_ ⇒ showExportDialog(key))),
        AppComponents.modalClose()
      )
      .show()
  }
}

