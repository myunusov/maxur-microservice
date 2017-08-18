package org.maxur.mserv.core.embedded.grizzly

import org.glassfish.grizzly.CompletionHandler
import org.glassfish.grizzly.http.server.HttpHandler
import org.glassfish.grizzly.http.server.Request
import org.glassfish.grizzly.http.server.Response
import org.glassfish.hk2.api.ServiceLocator
import org.glassfish.hk2.api.TypeLiteral
import org.glassfish.hk2.utilities.binding.AbstractBinder
import org.glassfish.jersey.grizzly2.httpserver.internal.LocalizationMessages.EXCEPTION_SENDING_ERROR_RESPONSE
import org.glassfish.jersey.internal.PropertiesDelegate
import org.glassfish.jersey.internal.inject.ReferencingFactory
import org.glassfish.jersey.internal.util.collection.Ref
import org.glassfish.jersey.process.internal.RequestScoped
import org.glassfish.jersey.server.ApplicationHandler
import org.glassfish.jersey.server.ContainerException
import org.glassfish.jersey.server.ContainerRequest
import org.glassfish.jersey.server.ContainerResponse
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.server.ServerProperties
import org.glassfish.jersey.server.ServerProperties.REDUCE_CONTEXT_PATH_SLASHES_ENABLED
import org.glassfish.jersey.server.ServerProperties.RESPONSE_SET_STATUS_OVER_SEND_ERROR
import org.glassfish.jersey.server.internal.ContainerUtils
import org.glassfish.jersey.server.spi.Container
import org.glassfish.jersey.server.spi.ContainerResponseWriter
import org.glassfish.jersey.server.spi.RequestScopedInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.OutputStream
import java.net.URI
import java.net.URISyntaxException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.ws.rs.core.Application
import javax.ws.rs.core.SecurityContext
import kotlin.reflect.KClass

/**
 * Jersey {@code Container} implementation based on Grizzly {@link org.glassfish.grizzly.http.server.HttpHandler}.
 */
class GrizzlyHttpContainer internal constructor(
        @Volatile private var appHandler: ApplicationHandler
) : HttpHandler(), Container {

    companion object {
        val log: Logger = LoggerFactory.getLogger(GrizzlyHttpContainer::class.java)
        val requestRef: TypeLiteral<Ref<Request>> = object : TypeLiteral<Ref<Request>>() {}
        val responseRef: TypeLiteral<Ref<Response>> = object : TypeLiteral<Ref<Response>>() {}
    }

    private val containerRequestFactory = ContainerRequestFactory(appHandler.configuration!!)

    private var isDestroyed: Boolean = false

    /**
     * Create a new Grizzly HTTP container.
     * @param application JAX-RS / Jersey application to be deployed on Grizzly HTTP container.
     * @param parentLocator parent HK2 service locator.
     */
    constructor(application: Application, parentLocator: ServiceLocator)
            : this(ApplicationHandler(application, GrizzlyBinder(), parentLocator))

    /**
     * An internal binder to enable Grizzly HTTP container specific types injection.
     *
     * This binder allows to inject underlying Grizzly HTTP request and response instances.
     * Note that since Grizzly `Request` class is not proxiable as it does not expose an empty constructor,
     * the injection of Grizzly request instance into singleton JAX-RS and Jersey providers is only supported via
     * [injection provider][javax.inject.Provider].
     */
    internal class GrizzlyBinder : AbstractBinder() {

        /**
         * Referencing factory for Grizzly request.
         */
        private class GrizzlyRequestReferencingFactory @Inject
        constructor(referenceFactory: Provider<Ref<Request>>) : ReferencingFactory<Request>(referenceFactory)

        /**
         * Referencing factory for Grizzly response.
         */
        private class GrizzlyResponseReferencingFactory @Inject
        constructor(referenceFactory: Provider<Ref<Response>>) : ReferencingFactory<Response>(referenceFactory)

        override fun configure() {
            bindFactories(GrizzlyRequestReferencingFactory::class, Request::class, requestRef)
            bindFactories(GrizzlyResponseReferencingFactory::class, Response::class, responseRef)
        }

        private fun <T : Any> bindFactories(
                refFactoryClass: KClass<out ReferencingFactory<T>>,
                clazz: KClass<T>,
                ref: TypeLiteral<Ref<T>>
        ) {
            bindFactory(refFactoryClass.java).to(clazz.java).proxy(false).`in`(RequestScoped::class.java)
            bindFactory(ReferencingFactory.referenceFactory<T>()).to(ref).`in`(RequestScoped::class.java)
        }
    }

    /** {@inheritDoc} */
    override fun start() {
        super.start()
        appHandler.onStartup(this)
    }

    /** {@inheritDoc} */
    override fun service(request: Request, response: Response) = try {
        log.debug("GrizzlyHttpContainer.service(...) started")
        appHandler.handle(containerRequestFactory.make(request, response))
    } finally {
        log.debug("GrizzlyHttpContainer.service(...) finished")
    }

    /** {@inheritDoc} */
    override fun getConfiguration() = appHandler.configuration!!

    /** {@inheritDoc} */
    override fun reload() = reload(appHandler.configuration)


    /** {@inheritDoc} */
    override fun reload(configuration: ResourceConfig) {
        appHandler.onShutdown(this)
        appHandler = ApplicationHandler(configuration, GrizzlyBinder())
        appHandler.onReload(this)
        appHandler.onStartup(this)
        containerRequestFactory.refreshProperty(configuration)
    }

    /** {@inheritDoc} */
    override fun getApplicationHandler() = if (isDestroyed) null else appHandler

    /** {@inheritDoc} */
    override fun destroy() {
        super.destroy()
        appHandler.onShutdown(this)
        isDestroyed = true
    }
}

/**
 * Grizzly container {@link PropertiesDelegate properties delegate}.
 */
private class RequestPropertiesDelegate(private val request: Request) : PropertiesDelegate {
    /** {@inheritDoc} */
    override fun getProperty(name: String): Any? = request.getAttribute(name)

    /** {@inheritDoc} */
    override fun getPropertyNames(): Collection<String>? = request.attributeNames

    /** {@inheritDoc} */
    override fun setProperty(name: String, value: Any) = request.setAttribute(name, value)

    /** {@inheritDoc} */
    override fun removeProperty(name: String) = request.removeAttribute(name)
}

private class ContainerRequestFactory(val configuration: ResourceConfig) {

    /**
     * Cached value of configuration property
     * [org.glassfish.jersey.server.ServerProperties.RESPONSE_SET_STATUS_OVER_SEND_ERROR].
     * If `true` method [org.glassfish.grizzly.http.server.Response.setStatus] is used over
     * [org.glassfish.grizzly.http.server.Response.sendError].
     */
    private var configSetStatusOverSendError: Boolean? = null

    /**
     * Cached value of configuration property
     * [org.glassfish.jersey.server.ServerProperties.REDUCE_CONTEXT_PATH_SLASHES_ENABLED].
     * If `true` method [org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpContainer.getRequestUri]
     * will reduce the of leading context-path slashes to only one.
     */
    private var configReduceContextPathSlashesEnabled: Boolean? = null

    init {
        refreshProperty(configuration)
    }

    fun refreshProperty(configuration: ResourceConfig) {
        configSetStatusOverSendError = configuration.propertyBy(RESPONSE_SET_STATUS_OVER_SEND_ERROR)
        configReduceContextPathSlashesEnabled = configuration.propertyBy(REDUCE_CONTEXT_PATH_SLASHES_ENABLED)
    }

    private fun ResourceConfig.propertyBy(key: String) = ServerProperties.getValue(
            configuration.properties, key, false, Boolean::class.javaObjectType
    )

    /**
     * Make ContainerRequest by Request and Response
     */
    fun make(request: Request, response: Response): ContainerRequest {
        val requestContext = ContainerRequest(
                request.baseUri(),
                request.requestUri(),
                request.method.methodString,
                request.securityContext(),
                RequestPropertiesDelegate(request)
        )
        requestContext.entityStream = request.inputStream
        for (headerName in request.headerNames) {
            requestContext.headers(headerName, request.getHeaders(headerName))
        }
        requestContext.setWriter(ResponseWriter(response, configSetStatusOverSendError!!))

        requestContext.requestScopedInitializer = RequestScopedInitializer { locator ->
            locator.getService<Ref<Request>>(GrizzlyHttpContainer.requestRef.type).set(request)
            locator.getService<Ref<Response>>(GrizzlyHttpContainer.responseRef.type).set(response)
        }
        return requestContext
    }

    private fun Request.securityContext(): SecurityContext = object : SecurityContext {
        /** {@inheritDoc} */
        override fun isUserInRole(role: String) = false

        /** {@inheritDoc} */
        override fun isSecure() = this@securityContext.isSecure

        /** {@inheritDoc} */
        override fun getUserPrincipal() = this@securityContext.userPrincipal

        /** {@inheritDoc} */
        override fun getAuthenticationScheme() = this@securityContext.authType
    }

    private fun Request.baseUri(): URI = try {
        this.uri(basePath())
    } catch (ex: URISyntaxException) {
        throw IllegalArgumentException(ex)
    }

    private fun Request.basePath(): String {
        return when {
            contextPath.isNullOrEmpty() -> "/"
            contextPath.endsWith('/').not() -> contextPath + "/"
            else -> contextPath
        }
    }

    private fun Request.requestUri(): URI = try {
        val serverAddress = uri().toString()
        var uri = if (configReduceContextPathSlashesEnabled!! && !contextPath.isNullOrEmpty()) {
            ContainerUtils.reduceLeadingSlashes(requestURI)
        } else {
            requestURI
        }
        val queryString = queryString
        if (queryString != null) {
            uri = uri + "?" + ContainerUtils.encodeUnsafeCharacters(queryString)
        }
        URI(serverAddress + uri)
    } catch (ex: URISyntaxException) {
        throw IllegalArgumentException(ex)
    }

    private fun Request.uri(basePath: String? = null): URI =
            URI(scheme, null, serverName, serverPort, basePath, null, null)

    private class ResponseWriter internal constructor(
            private val grizzlyResponse: Response,
            private val configSetStatusOverSendError: Boolean
    ) : ContainerResponseWriter {

        private val emptyCompletionHandler = object : CompletionHandler<Response> {

            /** {@inheritDoc} */
            override fun cancelled() = Unit

            /** {@inheritDoc} */
            override fun failed(throwable: Throwable) = Unit

            /** {@inheritDoc} */
            override fun completed(result: Response) = Unit

            /** {@inheritDoc} */
            override fun updated(result: Response) = Unit
        }

        private val name: String = if (GrizzlyHttpContainer.log.isDebugEnabled) {
            "ResponseWriter {id=${UUID.randomUUID()}, grizzlyResponse=${grizzlyResponse.hashCode()}}"
                    .also { GrizzlyHttpContainer.log.debug("{0} - init", it) }
        } else {
            "ResponseWriter"
        }

        /** {@inheritDoc} */
        override fun toString() = name

        /** {@inheritDoc} */
        override fun commit() {
            try {
                if (grizzlyResponse.isSuspended) {
                    grizzlyResponse.resume()
                }
            } finally {
                GrizzlyHttpContainer.log.debug("{0} - commit() called", name)
            }
        }

        /** {@inheritDoc} */
        override fun suspend(
                timeOut: Long,
                timeUnit: TimeUnit,
                timeoutHandler: ContainerResponseWriter.TimeoutHandler?
        ): Boolean = try {
            grizzlyResponse.suspend(timeOut, timeUnit, emptyCompletionHandler) {
                timeoutHandler?.onTimeout(this@ResponseWriter)
                // TODO should we return true in some cases instead?
                // Returning false relies on the fact that the timeoutHandler will resume the response.
                false
            }
            true
        } catch (ex: IllegalStateException) {
            false
        } finally {
            GrizzlyHttpContainer.log.debug("{0} - suspend(...) called", name)
        }

        /** {@inheritDoc} */
        @Throws(IllegalStateException::class)
        override fun setSuspendTimeout(timeOut: Long, timeUnit: TimeUnit) {
            try {
                grizzlyResponse.suspendContext.setTimeout(timeOut, timeUnit)
            } finally {
                GrizzlyHttpContainer.log.debug("{0} - setTimeout(...) called", name)
            }
        }

        /** {@inheritDoc} */
        @Throws(ContainerException::class)
        override fun writeResponseStatusAndHeaders(
                contentLength: Long,
                responseContext: ContainerResponse
        ): OutputStream =
                try {
                    val statusInfo = responseContext.statusInfo
                    when {
                        statusInfo.reasonPhrase == null -> grizzlyResponse.status = statusInfo.statusCode
                        else -> grizzlyResponse.setStatus(statusInfo.statusCode, statusInfo.reasonPhrase)
                    }
                    grizzlyResponse.contentLengthLong = contentLength
                    for ((key, values) in responseContext.stringHeaders) {
                        for (value in values) {
                            grizzlyResponse.addHeader(key, value)
                        }
                    }
                    grizzlyResponse.outputStream
                } finally {
                    GrizzlyHttpContainer.log.debug("{0} - writeResponseStatusAndHeaders() called", name)
                }

        /** {@inheritDoc} */
        override fun failure(error: Throwable) {
            if (grizzlyResponse.isCommitted) return
            try {
                try {
                    if (configSetStatusOverSendError) {
                        grizzlyResponse.reset()
                        grizzlyResponse.setStatus(500, "Request failed.")
                    } else {
                        grizzlyResponse.sendError(500, "Request failed.")
                    }
                } catch (ex: IllegalStateException) {
                    // a race condition externally committing the response can still occur...
                    GrizzlyHttpContainer.log.trace("Unable to reset failed response.", ex)
                } catch (ex: IOException) {
                    throw ContainerException(EXCEPTION_SENDING_ERROR_RESPONSE(500, "Request failed."), ex)
                }
            } finally {
                GrizzlyHttpContainer.log.debug("{0} - failure(...) called", name)
                rethrow(error)
            }
        }

        /** {@inheritDoc} */
        override fun enableResponseBuffering() = true

        /** {@inheritDoc} */
        private fun rethrow(error: Throwable) {
            when (error) {
                is RuntimeException -> throw error
                else -> throw ContainerException(error)
            }
        }
    }

}

