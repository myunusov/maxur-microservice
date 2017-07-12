package org.maxur.mserv.core.embedded.grizzly

import com.nhaarman.mockito_kotlin.verify
import com.winterbe.expekt.should
import org.glassfish.grizzly.http.util.HttpStatus
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.maxur.mserv.core.embedded.properties.StaticContent


class CLStaticHttpHandlerSpec : Spek({

    describe("a CLStaticHttpHandler") {

        on("Create CLStaticHttpHandler") {
            it("should return new CLStaticHttpHandler  instance") {
                val content = StaticContent("web", "classpath:/web/")
                val handler = CLStaticHttpHandler(CLStaticHttpHandlerSpec::class.java.classLoader, content)
                handler.should.be.not.`null`
            }
        }

        on("valid request of root folder") {
            it("should return status 200") {
                val content = StaticContent("web", "classpath:/web/")
                val handler = CLStaticHttpHandler(CLStaticHttpHandlerSpec::class.java.classLoader, content)
                val (response, request) = RequestUtil.resreq("/")
                handler.service(request, response)
                verify(response).setStatus(HttpStatus.OK_200)
            }
        }

        on("invalid request of root folder") {
            it("should return status 404") {
                val content = StaticContent("web", "classpath:/web/")
                val handler = CLStaticHttpHandler(CLStaticHttpHandlerSpec::class.java.classLoader, content)
                val (response, request) = RequestUtil.resreq("/error/")
                handler.service(request, response)
                verify(response).sendError(404)
            }
        }

        on("request of invalid root folder") {
            it("should return status 404") {
                val content = StaticContent("web", "classpath:/error/")
                val handler = CLStaticHttpHandler(CLStaticHttpHandlerSpec::class.java.classLoader, content)
                val (response, request) = RequestUtil.resreq("/")
                handler.service(request, response)
                verify(response).sendError(404)
            }
        }
    }

})

