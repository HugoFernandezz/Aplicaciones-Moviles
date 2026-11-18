package com.example.gestorempresa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.gestorempresa.data.local.entities.AjustesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AjustesDao {

    /** Emite null mientras el usuario no haya guardado nunca sus ajustes. */
    @Query("SELECT * FROM ajustes WHERE usuarioId = :usuarioId")
    fun observarAjustes(usuarioId: Long): Flow<AjustesEntity?>

    @Upsert
    suspend fun guardarAjustes(ajustes: AjustesEntity)
}
