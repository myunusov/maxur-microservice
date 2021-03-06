package org.maxur.mserv.frame.service.hk2

import gov.va.oia.HK2Utilities.HK2RuntimeInitializer
import org.glassfish.hk2.api.Factory
import org.glassfish.hk2.api.MultiException
import org.glassfish.hk2.api.ServiceLocator
import org.glassfish.hk2.api.ServiceLocatorState
import org.glassfish.hk2.api.TypeLiteral
import org.glassfish.hk2.utilities.ServiceLocatorUtilities
import org.glassfish.hk2.utilities.binding.AbstractBinder
import org.glassfish.hk2.utilities.binding.ScopedBindingBuilder
import org.glassfish.hk2.utilities.binding.ServiceBindingBuilder
import org.maxur.mserv.core.Either
import org.maxur.mserv.core.ErrorResult
import org.maxur.mserv.core.Result
import org.maxur.mserv.core.Value
import org.maxur.mserv.core.fold
import org.maxur.mserv.core.left
import org.maxur.mserv.core.right
import org.maxur.mserv.core.tryTo
import org.maxur.mserv.frame.LocatorConfig
import org.maxur.mserv.frame.LocatorImpl
import org.maxur.mserv.frame.kotlin.Locator
import org.maxur.mserv.frame.service.properties.Properties
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * Locator is the registry for services. The HK2 ServiceLocator adapter.
 * <p>
 * @property name The locator name
 * @param packages The set of package names to scan recursively.
 */
class LocatorHK2Impl @Inject constructor(override val name: String, packages: Set<String> = emptySet()) : LocatorImpl {

    private val locator: ServiceLocator by lazy {
        if (packages.isNotEmpty()) {
            HK2RuntimeInitializer.init(name, true, *packages.toTypedArray())
        } else {
            ServiceLocatorUtilities.createAndPopulateServiceLocator(name)
        }.also {
            ServiceLocatorUtilities.enableImmediateScope(it)
        }
    }

    /** {@inheritDoc} */
    override fun inject(injectMe: Any) {
        locator.inject(injectMe)
    }

    /** {@inheritDoc} */
    @Suppress("UNCHECKED_CAST")
    override fun <T> implementation(): T = locator as T

    /** {@inheritDoc} */
    override fun names(contractOrImpl: Class<*>): List<String> =
        locator.getAllServiceHandles(contractOrImpl).map { it.activeDescriptor.name }

    /** {@inheritDoc} */
    override fun <T> property(key: String, clazz: Class<T>): T? =
        locator.getService(Properties::class.java).read(key, clazz)

    /** {@inheritDoc} */
    override fun <T> service(contractOrImpl: Class<T>, name: String?): T? = tryTo {
        when (name) {
            null -> locator.getService<T>(contractOrImpl)
            else -> locator.getService(contractOrImpl, name)
        }
    }.result()

    /**
     * Return result or throws IllegalStateException
     * @return result
     */
    fun <E : Throwable, V> Result<E, V>.result(): V = when (this) {
        is Value -> value
        is ErrorResult -> throw convertError(error)
    }

    private tailrec fun convertError(error: Throwable): IllegalStateException = when (error) {
        is MultiException ->
            if (error.errors.size == 1)
                convertError(error.errors[0])
            else
                IllegalStateException(error)
        is IllegalStateException -> error
        else -> IllegalStateException(error)
    }

    /** {@inheritDoc} */
    override fun <T> services(contractOrImpl: Class<T>): List<T> =
        locator.getAllServices(contractOrImpl).map { it as T }

    /** {@inheritDoc} */
    override fun configurationError() = service(ErrorHandler::class.java)?.latestError

    /** {@inheritDoc} */
    override fun close() {
        if (locator.state == ServiceLocatorState.RUNNING)
            locator.shutdown()
    }

    override fun config(): org.maxur.mserv.frame.LocatorConfig = Config(this)

    class Config(locatorImpl: LocatorImpl) : LocatorConfig(locatorImpl) {

        override fun <T : Any> makeDescriptor(bean: Bean<T>, contract: Contract<T>?): Descriptor<T> =
            object : Descriptor<T>(bean, contract?.let { mutableSetOf(contract) } ?: mutableSetOf()) {
                @Suppress("UNCHECKED_CAST")
                override fun toSpecificContract(contract: Any) {
                    if (contract is TypeLiteral<*>)
                        contracts.add(ContractTypeLiteral(contract as TypeLiteral<in T>))
                }
            }

        override fun apply() {
            val binder = object : AbstractBinder() {
                override fun configure() {
                    descriptors.forEach { makeBinders(it) }
                }
            }
            ServiceLocatorUtilities.bind(locator.implementation<ServiceLocator>(), binder)
        }

        @Suppress("UNCHECKED_CAST")
        private fun AbstractBinder.makeBinders(descriptor: Descriptor<out Any>) {
            if (descriptor.contracts.isEmpty()) {
                throw IllegalStateException("Contract must be")
            }
            val binder = builder(descriptor)
            descriptor.contracts.forEach {
                when (it) {
                    is ContractClass -> binder.bind(it.contract.java as Class<Any>)
                    is ContractTypeLiteral -> binder.bind(it.literal as TypeLiteral<Any>)
                }
            }
            descriptor.name?.let { binder.named(it) }
            binder.initScope()
        }

        private fun AbstractBinder.builder(descriptor: Descriptor<out Any>): Binder<Any> {
            val bean = descriptor.bean
            return Binder(
                when (bean) {
                    is BeanFunction -> left(bindFactory(ServiceProvider(locator, bean.func)))
                    is BeanObject -> {
                        right(bind(bean.impl))
                    }
                    is BeanSingleton<out Any> -> {
                        if (bean.impl.isSubclassOf(Factory::class)) {
                            @Suppress("UNCHECKED_CAST")
                            val factoryClass: KClass<Factory<Any>> = bean.impl as KClass<Factory<Any>>
                            left(bindFactory(factoryClass.java))
                        } else
                            left(bind(bean.impl.java))
                    }
                    else -> throw IllegalStateException("Unknown description")
                } as Either<ServiceBindingBuilder<out Any>, ScopedBindingBuilder<out Any>>
            )
        }

        @Suppress("UNCHECKED_CAST")
        private class Binder<out T>(val binder: Either<ServiceBindingBuilder<out T>, ScopedBindingBuilder<out T>>) {
            fun bind(clazz: Class<Any>) {
                val arg = clazz as Class<in T>
                return binder.fold({ it.to(arg) }, { it.to(arg) })
            }
            fun bind(literal: TypeLiteral<Any>) =
                binder.fold({ it.to(literal) }, { it.to(literal) })

            fun named(name: String) =
                binder.fold({ it.named(name) }, { it.named(name) })

            fun initScope() =
                binder.fold({ it.`in`(Singleton::class.java) }, { it })
        }

        private class ServiceProvider<T>(val locator: LocatorImpl, val func: (Locator) -> T) : Factory<T> {
            val result: T by lazy { func.invoke(Locator(locator)) }
            /** {@inheritDoc} */
            override fun dispose(instance: T) = Unit

            /** {@inheritDoc} */
            override fun provide(): T = result
        }

        class ContractTypeLiteral<T : Any>(val literal: TypeLiteral<in T>) : Contract<T>()
    }
}
