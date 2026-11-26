package com.example.gestorempresa.auth

import com.example.gestorempresa.domain.auth.*

internal class FakePerfilCuentaService : PerfilCuentaService {
    val perfiles = mutableMapOf<String, PerfilCuentaRemoto>()
    var error: ErrorAuth? = null
    var creaciones = 0
    override suspend fun obtener(uid: String): PerfilCuentaRemoto? {
        error?.let { throw ErrorServicioAuth(it) }
        return perfiles[uid]
    }
    override suspend fun crearSiFalta(perfil: PerfilCuentaRemoto): PerfilCuentaRemoto {
        error?.let { throw ErrorServicioAuth(it) }
        return perfiles.getOrPut(perfil.uid) { creaciones++; perfil }
    }
    override suspend fun actualizar(perfil: PerfilCuentaRemoto) {
        error?.let { throw ErrorServicioAuth(it) }
        check(perfil.uid in perfiles)
        perfiles[perfil.uid] = perfil
    }
}
