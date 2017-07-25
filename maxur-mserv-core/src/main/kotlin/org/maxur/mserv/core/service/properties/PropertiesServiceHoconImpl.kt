package org.maxur.mserv.core.service.properties

import com.fasterxml.jackson.databind.ObjectMapper
import com.jasonclawson.jackson.dataformat.hocon.HoconFactory
import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Paths

class PropertiesServiceHoconImpl(val rawSource: PropertiesSource) : PropertiesSource {

    override val format: String get() = "Hocon"
    override val uri: URI get() = rawSource.uri ?: URI.create("classpath:///application.conf")
    override val rootKey: String get() = rawSource.rootKey ?: "DEFAULTS"

    private val objectMapper = ObjectMapper(HoconFactory())

    private var root: Config?

    override val isOpened: Boolean
        get() = root != null

    init {
        try {
            root = rootNode().getConfig(rootKey)
        } catch(e: ConfigException.Missing) {
            root = null
        }
    }

    private fun rootNode(): Config {
        return when {
            rawSource.uri == null -> ConfigFactory.load()
            uri.scheme == null -> loadFrom(File(uri.toString()))
            uri.scheme == "file" -> loadFrom(Paths.get(uri).toFile())
            uri.scheme == "classpath" -> ConfigFactory.load(
                    uri.toString().substring("classpath".length + 1).trimStart('/')
            )
            else -> throw IllegalArgumentException(
                    """Unsupported schema '${uri.scheme}' to properties source. Must be one of [file, classpath]"""
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <P> read(key: String, clazz: Class<P>): P? {
        when (clazz) {
            String::class.java -> return asString(key) as P
            Integer::class.java -> return asInteger(key) as P
            Long::class.java -> return asLong(key) as P
            URI::class.java -> return asURI(key) as P
            else -> return asObject(key, clazz) as P
        }
    }

    private fun loadFrom(file: File): Config =
            if (!file.exists())
                throw IllegalArgumentException("Properties file '${file.absolutePath}' is not found")
            else
                ConfigFactory.parseFile(file)


    override fun asURI(key: String): URI? {
        val string = asString(key)
        return when (string) {
            null -> null
            else -> URI.create(string)
        }
    }

    private fun asObject(key: String, clazz: Class<*>): Any? {
        try {
            return getValue(key, { it?.getObject(key) })
                    ?.let { objectMapper.readValue(it.render(), clazz) }
        } catch (e: IOException) {
            throw IllegalStateException("Configuration parameter '$key' is not parsed.", e)
        }
    }

    override fun asString(key: String): String? = getValue(key, { it?.getString(key) })
    override fun asLong(key: String): Long? = getValue(key, { it?.getLong(key) })
    override fun asInteger(key: String): Int? = getValue(key, { it?.getInt(key) })

    private fun <T> getValue(key: String, transform: (Config?) -> T?): T? {
        try {
            return transform.invoke(root)
        } catch (e: ConfigException.Missing) {
            throw IllegalStateException("Configuration parameter '$key' is not found.", e)
        }
    }

}