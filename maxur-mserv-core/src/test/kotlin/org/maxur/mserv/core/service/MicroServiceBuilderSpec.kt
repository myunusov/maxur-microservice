package org.maxur.mserv.core.service

import com.winterbe.expekt.should
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.maxur.mserv.core.Locator
import org.maxur.mserv.core.service.msbuilder.Java
import org.maxur.mserv.core.service.msbuilder.Kotlin
import org.maxur.mserv.core.service.properties.PropertiesSource
import java.net.URI
import kotlin.test.assertFailsWith

class MicroServiceBuilderSpec : Spek({

    describe("a empty micro-service") {

        on("Build micro-service without properties") {
            it("should return new micro-service") {
                val service = Kotlin.service { withoutProperties() }
                service.should.be.not.`null`
                val source = Locator.service(PropertiesSource::class)
                source.should.be.not.`null`
                source!!.format.should.be.equal("None")
                Locator.shutdown()
            }
            it("should return new micro-service for java client") {
                val service = Java.service()
                        .withoutProperties()
                        .build()
                service.should.be.not.`null`
                val source = Locator.service(PropertiesSource::class)
                source.should.be.not.`null`
                source!!.format.should.be.equal("None")
                Locator.shutdown()
            }
        }

        on("Build micro-service with Hocon properties without configuration") {
            it("should return new micro-service with default properties source") {
                val service = Kotlin.service {
                    properties {
                        format = "Hocon"
                    }
                }
                service.should.be.not.`null`
                val source = Locator.service(PropertiesSource::class)
                source.should.be.not.`null`
                source!!.apply {
                    format.should.be.equal("Hocon")
                    rootKey.should.be.equal("DEFAULTS")
                    uri.should.be.equal(URI("classpath:///application.conf"))
                }
                Locator.shutdown()
            }
            it("should return new micro-service with default properties source for java client") {
                val service = Java.service()
                        .properties("Hocon")
                        .build()
                service.should.be.not.`null`
                val source = Locator.service(PropertiesSource::class)
                source.should.be.not.`null`
                source!!.apply {
                    format.should.be.equal("Hocon")
                    rootKey.should.be.equal("DEFAULTS")
                    uri.should.be.equal(URI("classpath:///application.conf"))
                }
                Locator.shutdown()
            }
        }

        on("Build micro-service with Yaml properties without configuration") {
            it("should return new micro-service with default properties source") {
                val service = Kotlin.service {
                    properties {
                        format = "Yaml"
                    }
                }
                service.should.be.not.`null`
                val source = Locator.service(PropertiesSource::class)
                source.should.be.not.`null`
                source!!.apply {
                    format.should.be.equal("Yaml")
                    rootKey.should.be.equal("/")
                    uri.should.be.equal(URI("classpath:///application.yaml"))
                }
                Locator.shutdown()
            }
            it("should return new micro-service with default properties source for java client") {
                val service = Java.service()
                        .properties("Yaml")
                        .build()
                service.should.be.not.`null`
                val source = Locator.service(PropertiesSource::class)
                source.should.be.not.`null`
                source!!.apply {
                    format.should.be.equal("Yaml")
                    rootKey.should.be.equal("/")
                    uri.should.be.equal(URI("classpath:///application.yaml"))
                }
                Locator.shutdown()
            }
        }

        on("Build micro-service with Json properties without configuration") {
            it("should return new micro-service with default properties source") {
                val service = Kotlin.service {
                    properties {
                        format = "Json"
                    }
                }
                service.should.be.not.`null`
                val source = Locator.service(PropertiesSource::class)
                source.should.be.not.`null`
                source!!.apply {
                    format.should.be.equal("Json")
                    rootKey.should.be.equal("/")
                    uri.should.be.equal(URI("classpath:///application.json"))
                }
                Locator.shutdown()
            }
            it("should return new micro-service with default properties source for java client") {
                val service = Java.service()
                        .properties("Json")
                        .build()
                service.should.be.not.`null`
                val source = Locator.service(PropertiesSource::class)
                source.should.be.not.`null`
                source!!.apply {
                    format.should.be.equal("Json")
                    rootKey.should.be.equal("/")
                    uri.should.be.equal(URI("classpath:///application.json"))
                }
                Locator.shutdown()
            }
        }

        on("Build micro-service with Hocon properties file by url") {
            it("should return new micro-service") {
                val service = Kotlin.service {
                    properties {
                        format = "Hocon"
                        url = "src/test/resources/application.conf"
                    }
                }
                service.should.be.not.`null`
                val source = Locator.service(PropertiesSource::class)
                source.should.be.not.`null`
                source!!.apply {
                    format.should.be.equal("Hocon")
                    rootKey.should.be.equal("DEFAULTS")
                    uri.should.be.equal(URI("src/test/resources/application.conf"))
                }
                Locator.shutdown()
            }
            it("should return new micro-service for java client") {
                val service = Java.service()
                        .properties("Hocon")
                        .url("src/test/resources/application.conf")
                        .build()
                service.should.be.not.`null`
                val source = Locator.service(PropertiesSource::class)
                source.should.be.not.`null`
                source!!.apply {
                    format.should.be.equal("Hocon")
                    rootKey.should.be.equal("DEFAULTS")
                    uri.should.be.equal(URI("src/test/resources/application.conf"))
                }
                Locator.shutdown()
            }
        }

        on("Build micro-service with Hocon properties and rootKey") {
            it("should return new micro-service ") {
                val service = Kotlin.service {
                    properties {
                        format = "Hocon"
                        rootKey = "USER"
                    }
                }
                service.should.be.not.`null`
                val source = Locator.service(PropertiesSource::class)
                source.should.be.not.`null`
                source!!.apply {
                    format.should.be.equal("Hocon")
                    rootKey.should.be.equal("USER")
                    uri.should.be.equal(URI("classpath:///application.conf"))
                }
                Locator.shutdown()
            }
            it("should return new micro-service for java client") {
                val service = Java.service()
                        .properties("Hocon")
                        .rootKey("USER")
                        .build()
                service.should.be.not.`null`
                val source = Locator.service(PropertiesSource::class)
                source.should.be.not.`null`
                source!!.apply {
                    format.should.be.equal("Hocon")
                    rootKey.should.be.equal("USER")
                    uri.should.be.equal(URI("classpath:///application.conf"))
                }
                Locator.shutdown()
            }
        }

        afterEachTest {
            Locator.shutdown()
        }

        on("Build micro-service with Hocon properties with invalid configuration") {

            it("should throw error on unknown url scheme") {
                assertFailsWith<IllegalStateException> {
                    Kotlin.service {
                        properties {
                            format = "Hocon"
                            url = "error:///file.cfg"
                        }
                    }
                }

            }

            it("should throw error on unknown url scheme for java client") {
                assertFailsWith<IllegalStateException> {
                    Java.service()
                            .properties("Hocon")
                            .url("error:///file.cfg")
                            .build()
                }
            }

            it("should throw error on unknown file") {
                assertFailsWith<IllegalStateException> {
                    Kotlin.service {
                        properties {
                            format = "Hocon"
                            url = "file:///error.cfg"
                        }
                    }
                }
            }

            it("should throw error on unknown file for java client") {
                assertFailsWith<IllegalStateException> {
                    Java.service()
                            .properties("Hocon")
                            .url("file:///error.cfg")
                            .build()
                }
            }


            it("should throw error on unknown root key") {
                assertFailsWith<IllegalStateException> {
                    Kotlin.service {
                        properties {
                            format = "Hocon"
                            rootKey = "ERROR"
                        }
                    }
                }
            }

            it("should throw error on unknown root key for java client") {
                assertFailsWith<IllegalStateException> {
                    Java.service()
                            .properties("Hocon")
                            .rootKey("ERROR")
                            .build()
                }
            }
        }

        on("Build micro-service with default properties") {
            it("should return new micro-service") {
                val service = Kotlin.service {
                }
                service.should.be.not.`null`
                val source = Locator.service(PropertiesSource::class)
                source.should.be.not.`null`
                source!!.apply {
                    format.should.be.equal("Hocon")
                    rootKey.should.be.equal("DEFAULTS")
                    uri.should.be.equal(URI("classpath:///application.conf"))
                }
            }
        }
    }


    describe("a rest micro-service") {

    }

})
