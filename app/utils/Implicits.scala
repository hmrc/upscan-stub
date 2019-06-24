package utils

import java.util.Base64

object Implicits {
  implicit class Base64StringOps(input: String) {
    def base64encode(): String = {
      val encodedBytes = Base64.getEncoder.encode(input.getBytes("UTF-8"))
      new String(encodedBytes).replaceAll(System.lineSeparator, "")
    }

    def base64decode(): String =
      new String(Base64.getDecoder.decode(input))
  }
}
