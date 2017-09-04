package org.maxur.mserv.core

import org.maxur.mserv.core.kotlin.Locator
import kotlin.reflect.KClass

abstract class LocatorConfig(protected val locator: LocatorImpl) {

    protected val descriptors = mutableListOf<Descriptor<out Any>>()

    /**
     * Bind service [implementation]
     * @param implementation The Service implementation
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> bind(implementation: T): Descriptor<T> =
            descriptor(BeanObject(implementation), ContractSelf(implementation::class as KClass<in T>))

    /**
     * Bind service [implementation] class
     * @param implementation The Service implementation class
     */
    fun <T : Any> bind(implementation: KClass<in T>): Descriptor<T> =
            descriptor(BeanSingleton(implementation), ContractSelf(implementation))

    /**
     * Bind factory by [function]
     * @param function The Service creation function
     */
    fun <T : Any> bindFactory(function: (Locator) -> T): Descriptor<T> =
            descriptor(BeanFunction(function), ContractNone())

    private fun <T : Any> descriptor(bean: Bean<T>, contract: Contract<T>): Descriptor<T> =
            makeDescriptor(bean, contract).also { descriptors.add(it) }

    abstract protected fun <T : Any> makeDescriptor(bean: Bean<T>, contract: Contract<T>): Descriptor<T>

    abstract fun apply()

    abstract class Descriptor<T : Any>(var bean: Bean<T>, var contract: Contract<T>, var name: String? = null) {

        fun to(contract: Any): Descriptor<T> = this.apply {
            toContract(contract)
        }

        fun to(vararg contracts: KClass<in T>): Descriptor<T> = this.apply {
            contracts.forEach { toContract(it) }
        }


        @Suppress("UNCHECKED_CAST")
        private fun toContract(contract: Any) {
            toSpecificContract(contract)
            if (contract is KClass<*>) {
                when (this.contract) {
                    is ContractSet -> (this.contract as ContractSet).contracts.add(contract as KClass<T>)
                    else -> this.contract = ContractSet<T>(mutableSetOf(contract as KClass<T>))
                }
            }
        }

        abstract fun toSpecificContract(contract: Any)

        fun named(name: String): Descriptor<T> = this.apply {
            this.name = name
        }

    }

    abstract class Bean<T : Any>
    class BeanFunction<T : Any>(val func: (Locator) -> T) : Bean<T>()
    class BeanSingleton<T : Any>(val impl: KClass<in T>) : Bean<T>()
    @Suppress("UNCHECKED_CAST")
    class BeanObject<T : Any>(val impl: T) : Bean<T>()

    abstract class Contract<T : Any>
    class ContractNone<T : Any> : Contract<T>()
    class ContractSelf<T : Any>(val contract: KClass<in T>) : Contract<T>()
    class ContractSet<T : Any>(val contracts: MutableSet<KClass<in T>>) : Contract<T>()

}