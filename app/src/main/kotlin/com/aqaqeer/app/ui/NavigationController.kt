package com.aqaqeer.app.ui

import androidx.lifecycle.SavedStateHandle

internal class NavigationController(private val savedState: SavedStateHandle,
    private val routeAllowed: (Route) -> Boolean, private val beforeRoute: (Route) -> Unit,
    private val beforeBack: (Route) -> Unit) {
    val route = savedState.getStateFlow("route", "home")

    internal fun destinationStateKey(destination: String): String = destinationStateKey(RouteStorage.decode(destination))
    internal fun destinationStateKey(destination: Route): String {
        val encoded = RouteStorage.encode(destination)
        return if (destination is Route.EditMedication) encoded + ":" + savedState.get<String>("draft:$encoded").orEmpty() else encoded
    }
    fun navigate(destination: String) = navigate(RouteStorage.decode(destination))
    fun navigate(destination: Route) {
        if (!routeAllowed(destination)) return
        val encoded = RouteStorage.encode(destination)
        if (encoded == route.value) return
        if (destination is Route.EditMedication) savedState["draft:$encoded"] = java.util.UUID.randomUUID().toString()
        val stack = savedState.get<ArrayList<String>>("backStack") ?: arrayListOf("home").also {
            if (route.value != "home") it.add(route.value)
        }
        if (destination == Route.Home) stack.clear()
        if (stack.lastOrNull() != encoded) stack.add(encoded)
        savedState["backStack"] = stack
        changeRoute(destination)
    }
    private fun changeRoute(destination: Route) {
        if (!routeAllowed(destination)) {
            savedState["backStack"] = arrayListOf("home")
            savedState["route"] = "home"
            return
        }
        beforeRoute(destination)
        savedState["route"] = RouteStorage.encode(destination)
    }
    fun back() {
        beforeBack(RouteStorage.decode(route.value))
        val stack = savedState.get<ArrayList<String>>("backStack") ?: arrayListOf("home", route.value)
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        if (stack.isEmpty()) stack.add("home")
        savedState["backStack"] = stack
        changeRoute(RouteStorage.decode(stack.last()))
    }
}
