package org.maxur.mserv.frame.embedded.grizzly

/**
 * @author myunusov
 * @version 1.0
 * @since <pre>20.06.2017</pre>
 */
import org.glassfish.grizzly.Buffer
import org.glassfish.grizzly.WriteHandler
import org.glassfish.grizzly.http.Method
import org.glassfish.grizzly.http.io.NIOOutputStream
import org.glassfish.grizzly.http.server.HttpHandler
import org.glassfish.grizzly.http.server.Request
import org.glassfish.grizzly.http.server.Response
import org.glassfish.grizzly.http.server.StaticHttpHandlerBase
import org.glassfish.grizzly.http.util.Header
import org.glassfish.grizzly.http.util.HttpStatus
import org.glassfish.grizzly.http.util.MimeType
import org.glassfish.grizzly.memory.MemoryManager
import org.maxur.mserv.frame.domain.Path
import org.maxur.mserv.frame.embedded.properties.StaticContent
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.JarURLConnection
import java.net.MalformedURLException
import java.net.URI
import java.net.URL
import java.net.URLConnection
import java.nio.file.Paths
import java.util.jar.JarEntry

/**
 * [HttpHandler], which processes requests to a static resources resolved
 * by a given [ClassLoader].
 *
 * Create <tt>HttpHandler</tt>, which will handle requests
 * to the static resources resolved by the given class loader.
 *
 * @param classLoader [ClassLoader] to be used to resolve the resources
 * @param staticContent is the static content configuration
 *
 * @author Grizzly Team
 * @author Maxim Yunusov
 */
class StaticHttpHandler(
    staticContent: StaticContent,
    /** The Class Loader */
    val classLoader: ClassLoader = StaticHttpHandler::class.java.classLoader
) : StaticHttpHandlerBase() {

    companion object {
        /** The Logger */
        val log = LoggerFactory.getLogger(StaticHttpHandler::class.java)
    }

    private val resourceLocator: ResourceLocator = ResourceLocator(classLoader, staticContent)
    private val defaultPage: String = staticContent.page ?: "index.html"

    constructor(path: String, vararg roots: String) :
        this(StaticContent(Path(path), roots.map { URI.create(it) }.toTypedArray()))

    constructor(path: String, vararg roots: URI) :
        this(StaticContent(Path(path), arrayOf(*roots)))

    /**
     * {@inheritDoc}
     */
    @Throws(Exception::class)
    public override fun handle(resourcePath: String, request: Request, response: Response): Boolean {
        resourceLocator.find(resourcePath)?.let {
            if (it.isExist()) return it.handle(request, response)
        }
        log.trace("Resource not found $resourcePath")
        return false
    }

    fun respondedFile(url: URL): File {
        val file = File(url.toURI())
        if (!file.exists() || !file.isDirectory) return file
        val welcomeFile = File(file, "/$defaultPage")
        if (welcomeFile.exists() && welcomeFile.isFile) {
            return welcomeFile
        }
        return file
    }

    inner class CLFileResource(
        val url: URL,
        resourcePath: String,
        private val file: File = respondedFile(url),
        override val path: String = resourcePath
    ) : Resource() {

        override fun mustBeRedirected(resourcePath: String): Boolean =
            // TODO !this@StaticHttpHandler.isDirectorySlashOff &&
            file.isDirectory &&
                !resourcePath.endsWith("/")

        override fun isExist(): Boolean = file.exists()
        override fun process(request: Request, response: Response) {
            addToFileCache(request, response, file)
            StaticHttpHandlerBase.sendFile(response, file)
        }
    }

    fun JarURLConnection.close() {
        if (!useCaches) {
            jarFile.close()
        }
    }

    inner class JarURLInputStream(private val jarConnection: JarURLConnection, src: InputStream)
        : java.io.FilterInputStream(src) {
        override fun close() {
            try {
                super.close()
            } finally {
                jarConnection.close()
            }
        }
    }

    inner class JarResource(val url: URL) : Resource() {
        val urlConnection: URLConnection = url.openConnection()
        var filePath: String? = null
        override val path: String = url.path
            get() = filePath ?: field
        private var urlInputStream: JarURLInputStream? = null

        override fun mustBeRedirected(resourcePath: String): Boolean = false

        override fun isExist(): Boolean {
            val jarUrlConnection = urlConnection as JarURLConnection
            val (iinputStream, jarEntry) = makeInputStream(jarUrlConnection)
            if (iinputStream != null) {
                urlInputStream = JarURLInputStream(jarUrlConnection, iinputStream)
                filePath = jarEntry.name
                return true
            } else {
                jarUrlConnection.close()
                return false
            }
        }

        private fun makeInputStream(jarUrlConnection: JarURLConnection): Pair<InputStream?, JarEntry> {
            val jarFile = jarUrlConnection.jarFile
            var jarEntry: JarEntry = jarUrlConnection.jarEntry
            var iinputStream: InputStream? = jarFile.getInputStream(jarEntry)
            if (jarEntry.isDirectory || iinputStream == null) { // it's probably a folder
                jarEntry = jarFile.getJarEntry(welcomeResourcePath(jarEntry))
                if (jarEntry != null) {
                    iinputStream = jarFile.getInputStream(jarEntry)
                }
            }
            return Pair(iinputStream, jarEntry)
        }

        override fun process(request: Request, response: Response) {
            val jarFile = getJarFile(
                // we need that because url.getPath() may have url encoded symbols,
                // which are getting decoded when calling uri.getPath()
                URI(url.path).path
            )
            // if it's not a jar file - we don't know what to do with that
            // so not adding it to the file cache
            addTimeStampEntryToFileCache(request, response, jarFile)
            val stream = urlInputStream ?: urlConnection.getInputStream()!!
            sendResource(response, stream)
        }

        private fun addTimeStampEntryToFileCache(request: Request, response: Response, archive: File): Boolean {
            if (!isFileCacheEnabled) return false
            val fileCacheFilter = lookupFileCache(request.context) ?: return false
            val fileCache = fileCacheFilter.fileCache
            if (!fileCache.isEnabled) return false
            StaticHttpHandlerBase.addCachingHeaders(response, archive)
            fileCache.add(request.request, archive.lastModified())
            return true
        }

        @Throws(MalformedURLException::class, FileNotFoundException::class)
        private fun getJarFile(path: String): File {
            val jarDelimIdx = path.indexOf("!/")
            if (jarDelimIdx == -1) throw MalformedURLException("The jar file delimiter were not found")
            val file = File(path.substring(0, jarDelimIdx))
            if (file.exists() && file.isFile) return file
            throw FileNotFoundException("The jar file was not found")
        }
    }

    private fun welcomeResourcePath(jarEntry: JarEntry): String {
        val welcomeResource = if (jarEntry.name.endsWith("/"))
            "${jarEntry.name}$defaultPage" else "${jarEntry.name}/$defaultPage"
        return welcomeResource
    }

    // OSGi resource
    inner class BundleResource(var url: URL, val mayBeFolder: Boolean) : Resource() {

        override fun mustBeRedirected(resourcePath: String): Boolean = false

        var urlConnection: URLConnection = url.openConnection()

        override val path: String = url.path

        override fun isExist(): Boolean {
            if (mayBeFolder && urlConnection.contentLength <= 0) { // looks like a folder?
                // check if there's a welcome resource
                val welcomeUrl = classLoader.getResource("${url.path}/$defaultPage")
                if (welcomeUrl != null) {
                    url = welcomeUrl
                    urlConnection = welcomeUrl.openConnection()
                }
            }
            return true
        }

        override fun process(request: Request, response: Response) {
            sendResource(response, urlConnection.getInputStream())
        }
    }

    inner class UnknownResource(val url: URL) : Resource() {

        override fun mustBeRedirected(resourcePath: String): Boolean = false

        override val path: String = url.path

        var urlConnection: URLConnection = url.openConnection()

        override fun isExist(): Boolean = true

        override fun process(request: Request, response: Response) {
            sendResource(response, urlConnection.getInputStream())
        }
    }

    inner class RedirectedResource(url: String) : Resource() {

        override fun mustBeRedirected(resourcePath: String): Boolean = false

        override val path: String = url

        override fun isExist(): Boolean = true

        // Redirect to the same url, but with trailing slash
        override fun process(request: Request, response: Response) {
            response.setStatus(HttpStatus.MOVED_PERMANENTLY_301)
            response.setHeader(Header.Location, response.encodeRedirectURL("$path/"))
        }
    }

    abstract inner class Resource {

        abstract val path: String

        // url may point to a folder or a file
        fun handle(request: Request, response: Response): Boolean =
            // If it's not HTTP GET - return method is not supported status
            if (isGet(request))
                success(response, request)
            else
                methodIsNotAllowed(path, request, response)

        private fun isGet(request: Request) = Method.GET == request.method

        private fun success(response: Response, request: Request): Boolean {
            pickupContentType(response, path)
            process(request, response)
            return true
        }

        private fun methodIsNotAllowed(resource: String, request: Request, response: Response): Boolean {
            log.trace("File found $resource, but HTTP method ${request.method} is not allowed")
            response.setStatus(HttpStatus.METHOD_NOT_ALLOWED_405)
            response.setHeader(Header.Allow, "GET")
            return true
        }

        abstract fun isExist(): Boolean

        abstract fun process(request: Request, response: Response)

        protected fun pickupContentType(response: Response, path: String) {
            if (response.response.isContentTypeSet) return
            val dot = path.lastIndexOf('.')
            if (dot > 0) {
                val ext = path.substring(dot + 1)
                val ct = MimeType.get(ext)
                if (ct != null) {
                    response.contentType = ct
                }
            } else {
                response.contentType = MimeType.get("html")
            }
        }

        @Throws(IOException::class)
        protected fun sendResource(response: Response, input: InputStream) {
            response.setStatus(HttpStatus.OK_200)
            response.addDateHeader(Header.Date, System.currentTimeMillis())
            val chunkSize = 8192
            response.suspend()
            val outputStream = response.nioOutputStream
            outputStream.notifyCanWrite(NonBlockingDownloadHandler(response, outputStream, input, chunkSize))
        }

        private inner class NonBlockingDownloadHandler
        internal constructor(private val response: Response,
            private val outputStream: NIOOutputStream,
            private val inputStream: InputStream,
            private val chunkSize: Int
        ) : WriteHandler {
            private val mm: MemoryManager<*> = response.getRequest().context.memoryManager
            @Throws(Exception::class)
            override fun onWritePossible() {
                log.trace("[onWritePossible]")
                // send CHUNK of data
                val isWriteMore = sendChunk()
                if (isWriteMore) {
                    // if there are more bytes to be sent - reregister this WriteHandler
                    outputStream.notifyCanWrite(this)
                }
            }

            override fun onError(t: Throwable) {
                log.trace("[onError] ", t)
                response.setStatus(500, t.message)
                complete(true)
            }

            /**
             * Send next CHUNK_SIZE of file
             */
            @Throws(IOException::class)
            private fun sendChunk(): Boolean {
                val buffer: Buffer? = if (!mm.willAllocateDirect(chunkSize)) {
                    indirectAllocateBuffer()
                } else {
                    directAllocateBuffer()
                }
                if (buffer == null) {
                    complete(false)
                    return false
                }
                // mark it available for disposal after content is written
                buffer.allowBufferDispose(true)
                buffer.trim()
                // write the Buffer
                outputStream.write(buffer)
                return true
            }

            private fun directAllocateBuffer(): Buffer? {
                val buf = ByteArray(chunkSize)
                val len = inputStream.read(buf)
                if (len <= 0) {
                    return null
                }
                val buffer: Buffer = mm.allocate(len)
                buffer.put(buf)
                return buffer
            }

            private fun indirectAllocateBuffer(): Buffer? {
                val buffer: Buffer = mm.allocate(chunkSize)
                val len: Int =
                    if (buffer.isComposite) readCompositeBuffer(buffer)
                    else inputStream.read(buffer.array(),
                        buffer.position() + buffer.arrayOffset(),
                        chunkSize)
                if (len > 0) {
                    buffer.position(buffer.position() + len)
                } else {
                    buffer.dispose()
                }
                if (len > 0) return buffer else return null
            }

            private fun readCompositeBuffer(buffer: Buffer): Int {
                val bufferArray = buffer.toBufferArray()
                val size = bufferArray.size()
                val buffers = bufferArray.array
                var lenCounter = 0
                for (i in 0..size - 1) {
                    val subBuffer = buffers[i]
                    val subBufferLen = subBuffer.remaining()
                    val justReadLen = inputStream.read(subBuffer.array(),
                        subBuffer.position() + subBuffer.arrayOffset(),
                        subBufferLen)
                    if (justReadLen > 0) lenCounter += justReadLen
                    if (justReadLen < subBufferLen) break
                }
                bufferArray.restore()
                bufferArray.recycle()
                return if (lenCounter > 0) lenCounter else -1
            }

            /**
             * Complete the download
             */
            private fun complete(isError: Boolean) {
                closeStream(inputStream, isError)
                closeStream(outputStream, isError)
                if (response.isSuspended) {
                    response.resume()
                } else {
                    response.finish()
                }
            }

            private fun closeStream(stream: Closeable, isError: Boolean) {
                try {
                    stream.close()
                } catch (e: IOException) {
                    if (!isError) {
                        response.setStatus(500, e.message)
                    }
                }
            }
        }

        abstract fun mustBeRedirected(resourcePath: String): Boolean
    }

    inner class ResourceLocator(val classLoader: ClassLoader, staticContent: StaticContent) {

        private val roots = makeRoots(staticContent)

        private fun makeRoots(staticContent: StaticContent): Set<Root> {
            val set = staticContent.roots
                .map { makeRoot(it) }
                .filterNotNull()
                .map { it.validate() }
                .toHashSet()

            if (set.isNotEmpty()) {
                return set
            } else {
                return setOf(CLRoot("", classLoader))
            }
        }

        private fun makeRoot(uri: URI): Root? =
            when (uri.scheme) {
                null -> FileRoot(File(uri.toString()))
                "file" -> FileRoot(Paths.get(uri).toFile())
                "classpath" -> CLRoot(uri, classLoader)
                else -> null
            }

        //@todo #2 DEV move "check-non-slash-terminated-folders" to web-app properties
        private val CHECK_NON_SLASH_TERMINATED_FOLDERS_PROP =
            StaticHttpHandler::class.java.name + ".check-non-slash-terminated-folders"

        /**
         * <tt>true</tt> (default) if we want to double-check the resource requests,
         * that don't have terminating slash if they represent a folder and try
         * to retrieve a welcome resource from the folder.
         */
        private val CHECK_NON_SLASH_TERMINATED_FOLDERS =
            System.getProperty(CHECK_NON_SLASH_TERMINATED_FOLDERS_PROP) == null ||
                java.lang.Boolean.getBoolean(CHECK_NON_SLASH_TERMINATED_FOLDERS_PROP)

        fun find(resourcePath: String): Resource? {
            val path = resourcePath.trimStart('/')
            if (path.isEmpty() || path.endsWith("/")) {
                return findDefaultPage(path)
            }
            return roots
                .map { it.lookupResource(path, true) }
                .firstOrNull()?.let {
                return if (it.mustBeRedirected(resourcePath)) RedirectedResource(resourcePath)
                else it
            } ?: if (CHECK_NON_SLASH_TERMINATED_FOLDERS) {
                // So try to add index.html to double-check.
                // For example null will be returned for a folder inside a jar file.
                // some ClassLoaders return null if a URL points to a folder.
                return findDefaultPage(path + "/")
            } else null
        }

        private fun findDefaultPage(folderPath: String): Resource? = roots
            .map { it.lookupResource(folderPath + defaultPage, false) }
            .firstOrNull()?.let {
            return it
        }
    }

    inner class FileResource(
        folder: File,
        resourcePath: String,
        private val file: File = File(folder, resourcePath)
    ) : Resource() {

        override fun mustBeRedirected(resourcePath: String): Boolean =
            // TODO !this@StaticHttpHandler.isDirectorySlashOff &&
            file.isDirectory &&
                !resourcePath.endsWith("/")

        override val path: String = file.path

        override fun isExist(): Boolean = this.file.exists()

        override fun process(request: Request, response: Response) {
            if (file.exists()) {
                addToFileCache(request, response, file)
                sendFile(response, file)
            }
        }
    }

    inner class FileRoot(val file: File) : Root {

        override fun validate(): Root = this
        override fun lookupResource(resourcePath: String, mayBeFolder: Boolean): Resource? {
            return FileResource(file, resourcePath)
        }
    }

    inner class CLRoot(val path: String, val classLoader: ClassLoader) : Root {

        constructor(uri: URI, classLoader: ClassLoader) : this(
            uri.toString().substring("classpath".length + 1).trimStart('/'),
            classLoader
        )

        override fun validate(): Root {
            if (this.path.endsWith("/")) this.path
            else throw IllegalArgumentException("Doc root should end with slash ('/')")
            return this
        }

        override fun lookupResource(resourcePath: String, mayBeFolder: Boolean): StaticHttpHandler.Resource? {
            classLoader.getResource(path + resourcePath)?.also {
                val resource = make(resourcePath, it, mayBeFolder)
                if (resource.isExist()) return resource
            }
            return null
        }

        private fun make(path: String, url: URL, mayBeFolder: Boolean): StaticHttpHandler.Resource =
            when (url.protocol) {
                "file" -> CLFileResource(url, path)
                "jar" -> JarResource(url)
                "bundle" -> BundleResource(url, mayBeFolder)
                else -> UnknownResource(url)
            }
    }
}

interface Root {
    fun validate(): Root
    fun lookupResource(resourcePath: String, mayBeFolder: Boolean): StaticHttpHandler.Resource?
}
