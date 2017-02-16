package com.karasiq.shadowcloud.streams

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionModule, HashingMethod, HashingModule}
import com.karasiq.shadowcloud.index.Chunk

import scala.language.postfixOps
import scala.util.Random

class ChunkEncryptor(encryptionMethod: EncryptionMethod, hashingMethod: HashingMethod) extends GraphStage[FlowShape[Chunk, Chunk]] {
  val inlet = Inlet[Chunk]("ChunkEncryptor.in")
  val outlet = Outlet[Chunk]("ChunkEncryptor.out")

  val shape = FlowShape(inlet, outlet)

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {
    val encryptionModule = EncryptionModule(encryptionMethod)
    val hashingModule = HashingModule(hashingMethod)
    var parameters = encryptionModule.createParameters()
    var encryptedChunks = 0
    var changeKeyIn = Random.nextInt(256)

    setHandler(inlet, new InHandler {
      def onPush(): Unit = {
        val chunk = grab(inlet)
        require(chunk.checksum.method == hashingModule.method)
        val encryptedData = encryptionModule.encrypt(chunk.data.plain, parameters)
        val encryptedHash = hashingModule.createHash(encryptedData)
        val encryptedChunk = chunk.copy(
          checksum = chunk.checksum.copy(encryptedSize = encryptedData.length, encryptedHash = encryptedHash),
          encryption = parameters,
          data = chunk.data.copy(encrypted = encryptedData)
        )

        // Update IV/key
        encryptedChunks += 1
        if (encryptedChunks > changeKeyIn) {
          parameters = encryptionModule.createParameters()
          changeKeyIn = Random.nextInt(256)
          encryptedChunks = 0
        } else {
          parameters = encryptionModule.updateParameters(parameters)
        }

        push(outlet, encryptedChunk)
      }
    })

    setHandler(outlet, new OutHandler {
      def onPull(): Unit = {
        tryPull(inlet)
      }
    })
  }
}