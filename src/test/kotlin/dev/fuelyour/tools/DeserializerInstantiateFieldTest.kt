package dev.fuelyour.tools

import io.kotlintest.specs.StringSpec

class DeserializerInstantiateFieldTest :
    Deserializer by DeserializerImpl(), StringSpec() {

  init {
    //todo write extensive tests around the Field type
  }
}