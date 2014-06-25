package scredis.protocol

import akka.util.ByteString

import scredis.exceptions._

import scala.util.{ Try, Success, Failure }
import scala.concurrent.Promise

import java.nio.ByteBuffer

abstract class Request[A](command: Command, args: Any*) {
  private val promise = Promise[A]()
  private var _buffer: ByteBuffer = null
  private var _bytes: Array[Byte] = null
  val future = promise.future
  
  private[scredis] def encode(): Unit = command match {
    case x: ZeroArgCommand  => _bytes = x.encoded
    case _                  => _buffer = command.encode(args.toList)
  }
  
  private[scredis] def encoded: Either[Array[Byte], ByteBuffer] = if (_bytes != null) {
    Left(_bytes)
  } else {
    Right(_buffer)
  }
  
  private[scredis] def complete(response: Response): Unit = {
    response match {
      case ErrorResponse(message) => promise.failure(RedisErrorResponseException(message))
      case response => try {
        promise.success(decode(response))
      } catch {
        case e: Throwable => promise.failure(
          RedisProtocolException(s"Unexpected response: $response", e)
        )
      }
    }
    Protocol.release()
  }
  
  private[scredis] def failure(throwable: Throwable): Unit = {
    promise.failure(throwable)
    Protocol.release()
  }
  
  def decode: PartialFunction[Response, A]
  def hasArguments = args.size > 0
  
}
