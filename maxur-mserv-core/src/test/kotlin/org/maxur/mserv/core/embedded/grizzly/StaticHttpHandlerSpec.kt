package org.maxur.mserv.core.embedded.grizzly

import com.nhaarman.mockito_kotlin.verify
import org.assertj.core.api.Assertions.assertThat
import org.glassfish.grizzly.http.Method
import org.glassfish.grizzly.http.util.HttpStatus
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import org.maxur.mserv.core.embedded.properties.WebAppProperties

@RunWith(JUnitPlatform::class)
class StaticHttpHandlerSpec : Spek({

    describe("a StaticHttpHandler") {

        on("Create StaticHttpHandler on folder by classpath") {
            it("should return new StaticHttpHandler instance") {
                val handler = StaticHttpHandler("web", "classpath:/web/")
                assertThat(handler).isNotNull()
            }
        }

        on("Create StaticHttpHandler on folder") {
            it("should return new StaticHttpHandler instance") {
                val handler = StaticHttpHandler("web", "src/test/resources/web/")
                assertThat(handler).isNotNull()
            }
        }

        on("valid request of jar") {
            it("should return new StaticHttpHandler instance") {
                val handler = StaticHttpHandler("web", WebAppProperties.SWAGGER_URL)
                val (response, request) = RequestUtil.resreq("/")
                handler.service(request, response)
                verify(response).setStatus(HttpStatus.OK_200)
            }
        }

        on("valid request of file from jar") {
            it("should return status 200") {
                val handler = StaticHttpHandler("web", WebAppProperties.SWAGGER_URL)
                val (response, request) = RequestUtil.resreq("/index.html")
                handler.service(request, response)
                verify(response).setStatus(HttpStatus.OK_200)
            }
        }

        on("request of invalid file from jar") {
            it("should return status 404") {
                val handler = StaticHttpHandler("web", WebAppProperties.SWAGGER_URL)
                val (response, request) = RequestUtil.resreq("/error.html")
                handler.service(request, response)
                verify(response).sendError(404)
            }
        }

        on("valid request of root folder in classpath") {
            it("should return status 200") {
                val handler = StaticHttpHandler("web", "classpath:/web/")
                val (response, request) = RequestUtil.resreq("/")
                handler.service(request, response)
                verify(response).setStatus(HttpStatus.OK_200)
            }
        }

        on("invalid request of root folder in classpath") {
            it("should return status 404") {
                val handler = StaticHttpHandler("web", "classpath:/web/")
                val (response, request) = RequestUtil.resreq("/error/")
                handler.service(request, response)
                verify(response).sendError(404)
            }
        }

        on("valid request of root folder in fs") {
            it("should return status 200") {
                val handler = StaticHttpHandler("web", "src/test/resources/web/")
                val (response, request) = RequestUtil.resreq("/")
                handler.service(request, response)
                verify(response).setStatus(HttpStatus.OK_200)
            }
        }

        on("valid request of page in fs") {
            it("should return status 200") {
                val handler = StaticHttpHandler("web", "src/test/resources/web/")
                val (response, request) = RequestUtil.resreq("/index.html")
                handler.service(request, response)
                verify(response).setStatus(HttpStatus.OK_200)
            }
        }

        on("valid request of page by absolute path in fs") {
            it("should return status 200") {
                val folder = System.getProperty("user.dir").replace('\\', '/')
                val handler = StaticHttpHandler("web", "file:///$folder/src/test/resources/web/")
                val (response, request) = RequestUtil.resreq("/index.html")
                handler.service(request, response)
                verify(response).setStatus(HttpStatus.OK_200)
            }
        }

        on("empty request in fs") {
            it("should return status 200 and default page") {
                val handler = StaticHttpHandler("web", "src/test/resources/web/")
                val (response, request) = RequestUtil.resreq("")
                handler.service(request, response)
                verify(response).setStatus(HttpStatus.OK_200)
            }
        }

        on("request of folder without tailed slash in fs") {
            it("should return status 301 and redirect request") {
                val handler = StaticHttpHandler("web", "src/test/resources/web/")
                val (response, request) = RequestUtil.resreq("folder")
                handler.service(request, response)
                verify(response).setStatus(HttpStatus.MOVED_PERMANENTLY_301)
            }
        }

        on("valid request of root folder with redirect") {
            it("should return status 200 and default page") {
                val handler = StaticHttpHandler("web", "src/test/resources/web/")
                val (response, request) = RequestUtil.resreq("")
                handler.service(request, response)
                verify(response).setStatus(HttpStatus.OK_200)
            }
        }

        on("request of invalid root folder") {
            it("should return status 404") {
                val handler = StaticHttpHandler("web", "classpath:/error/")
                val (response, request) = RequestUtil.resreq("/")
                handler.service(request, response)
                verify(response).sendError(404)
            }
        }

        on("valid request of root folder with invalid method") {
            it("should return status 404") {
                val handler = StaticHttpHandler("web", "classpath:/web/")
                val (response, request) = RequestUtil.resreq("/", Method.POST)
                handler.service(request, response)
                verify(response).setStatus(HttpStatus.METHOD_NOT_ALLOWED_405)
            }
        }
    }

})

