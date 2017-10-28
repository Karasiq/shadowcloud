package com.karasiq.shadowcloud.webapp.components.keys

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import akka.util.ByteString
import org.scalajs.dom.MouseEvent
import org.scalajs.dom.html.TextArea
import rx.Var

import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.model.keys.KeySet
import com.karasiq.shadowcloud.webapp.components.common.AppComponents
import com.karasiq.shadowcloud.webapp.context.AppContext
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext
import com.karasiq.shadowcloud.webapp.utils.ExportUtils

object KeysView {
  def apply()(implicit context: AppContext, kc: KeysContext): KeysView = {
    new KeysView()
  }
}

class KeysView()(implicit context: AppContext, kc: KeysContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    val heading = Seq[ModifierT](
      context.locale.keyId,
      context.locale.keyEncAlgorithm,
      context.locale.keySignAlgorithm
    )

    val rows = kc.keys.map { keyChain ⇒
      val encSet = keyChain.encKeys.toSet
      val decSet = keyChain.decKeys.toSet
      val activeKeys = (encSet ++ decSet).toVector.sortBy(_.id)
      val activeRows = activeKeys.map { key ⇒
        TableRow(Seq(key.id.toString, key.encryption.method.algorithm, key.signing.method.algorithm), TableRowStyle.active,
          onclick := Callback.onClick(_ ⇒ showKeyDialog(key, encSet.contains(key), decSet.contains(key))))
      }

      val nonActiveRows = keyChain.disabledKeys.map { key ⇒
        TableRow(Seq(key.id.toString, key.encryption.method.algorithm, key.signing.method.algorithm), textDecoration.`line-through`,
          onclick := Callback.onClick(_ ⇒ showKeyDialog(key, forEncryption = false, forDecryption = false)))
      }

      activeRows ++ nonActiveRows
    }

    div(
      GridSystem.mkRow(renderAddButtons()),
      GridSystem.mkRow(PagedTable(Var(heading), rows))
    )
  }

  private[this] def renderAddButtons(): TagT = {
    def showExportDialog(key: KeySet): Unit = {
      val keyString = ExportUtils.encodeKey(key)
      Modal()
        .withTitle(context.locale.exportKey)
        .withBody(FormInput.textArea("", keyString, rows := 20, readonly, onclick := { (e: MouseEvent) ⇒
          val textArea = e.target.asInstanceOf[TextArea]
          textArea.focus()
          textArea.select()
        }))
        .withButtons(AppComponents.modalClose())
        .show(backdrop = false)
    }

    def showGenerateDialog(): Unit = {
      def doGenerate(propsString: String): Unit = {
        val props = SerializedProps("hocon", ByteString(propsString))
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

    val generateButton = Button(ButtonStyle.success)(
      context.locale.generateKey,
      onclick := Callback.onClick(_ ⇒ showGenerateDialog())
    )

    val importButton = Button(ButtonStyle.primary)(
      context.locale.importKey,
      onclick := Callback.onClick(_ ⇒ showImportDialog())
    )

    ButtonGroup(ButtonGroupSize.default, generateButton, importButton)
  }

  private[this] def showKeyDialog(key: KeySet, forEncryption: Boolean, forDecryption: Boolean): Unit = {
    def updatePermissions(forEncryption: Boolean, forDecryption: Boolean): Unit = {
      context.api.modifyKey(key.id, forEncryption, forDecryption)
        .foreach(_ ⇒ kc.updateAll())
    }

    def renderPermissions() = {
      val forEncryptionRx = Var(forEncryption)
      val forDecryptionRx = Var(forDecryption)

      forEncryptionRx.triggerLater(updatePermissions(forEncryptionRx.now, forDecryptionRx.now))
      forDecryptionRx.triggerLater(updatePermissions(forEncryptionRx.now, forDecryptionRx.now))

      Form(
        FormInput.checkbox(context.locale.keyForEncryption, forEncryptionRx.reactiveInput),
        FormInput.checkbox(context.locale.keyForDecryption, forDecryptionRx.reactiveInput)
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
      .withButtons(AppComponents.modalClose())
      .show()
  }
}

