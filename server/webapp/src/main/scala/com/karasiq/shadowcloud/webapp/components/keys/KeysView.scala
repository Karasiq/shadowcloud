package com.karasiq.shadowcloud.webapp.components.keys

import akka.util.ByteString
import org.scalajs.dom.MouseEvent
import org.scalajs.dom.html.TextArea
import rx.{Rx, Var}

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.model.keys.KeySet
import com.karasiq.shadowcloud.model.keys.KeyProps.RegionSet
import com.karasiq.shadowcloud.webapp.components.common.AppComponents
import com.karasiq.shadowcloud.webapp.components.region.RegionContext
import com.karasiq.shadowcloud.webapp.context.AppContext
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext
import com.karasiq.shadowcloud.webapp.utils.{Blobs, ExportUtils}

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
      val newKeyRx = Var("")
      Modal()
        .withTitle(context.locale.importKey)
        .withBody(Form(FormInput.textArea("", rows := 20, newKeyRx.reactiveInput)))
        .withButtons(
          AppComponents.modalSubmit(newKeyRx.map(_.isEmpty).reactiveHide, onclick := Callback.onClick { _ ⇒
            val key = ExportUtils.decodeKey(newKeyRx.now)
            context.api.addKey(key).foreach(_ ⇒ kc.updateAll())
          }),
          AppComponents.modalClose()
        )
        .show()
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
    val keyString = ExportUtils.encodeKey(key)

    def downloadKey(): Unit = {
      Blobs.saveBlob(Blobs.fromString(keyString, "application/json"), s"${key.id}.json")
    }

    Modal()
      .withTitle(context.locale.exportKey)
      .withBody(FormInput.textArea("", keyString, rows := 20, readonly, onclick := { (e: MouseEvent) ⇒
        val textArea = e.target.asInstanceOf[TextArea]
        textArea.focus()
        textArea.select()
      }))
      .withButtons(
        Button(ButtonStyle.success)(context.locale.downloadFile, onclick := Callback.onClick(_ ⇒ downloadKey())),
        AppComponents.modalClose()
      )
      .show(backdrop = false)
  }

  private[this] def showKeyDialog(key: KeySet, regionSet: RegionSet, forEncryption: Boolean, forDecryption: Boolean): Unit = {
    def updatePermissions(regionSet: RegionSet, forEncryption: Boolean, forDecryption: Boolean): Unit = {
      context.api.modifyKey(key.id, regionSet, forEncryption, forDecryption).foreach(_ ⇒ kc.updateAll())
    }

    def renderPermissions() = {
      val forEncryptionRx = Var(forEncryption)
      val forDecryptionRx = Var(forDecryption)
      val regionSelector = FormInput.multipleSelect(context.locale.regions, rc.regions.map(_.regions.keys.toVector.map(id ⇒ FormSelectOption(id, id))))
      regionSelector.selected() = regionSet.toVector

      Rx((forEncryptionRx(), forDecryptionRx(), regionSelector.selected()))
        .triggerLater(updatePermissions(regionSelector.selected.now.toSet, forEncryptionRx.now, forDecryptionRx.now))
      
      Form(
        FormInput.checkbox(context.locale.keyForEncryption, forEncryptionRx.reactiveInput),
        FormInput.checkbox(context.locale.keyForDecryption, forDecryptionRx.reactiveInput),
        regionSelector
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

