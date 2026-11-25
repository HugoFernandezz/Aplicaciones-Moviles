package com.example.gestorempresa.ui.home

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gestorempresa.data.local.HomeMovement
import com.example.gestorempresa.data.local.HomeAttachment
import com.example.gestorempresa.data.media.AttachmentStorage
import com.example.gestorempresa.data.local.entities.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlinx.coroutines.launch

internal fun positiveNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
    ?.takeIf { it.isFinite() && it > 0 && it <= 1_000_000_000 }
internal fun moneyCents(text: String): Long? = try {
    BigDecimal(text.trim().replace(',', '.')).setScale(2, RoundingMode.UNNECESSARY)
        .movePointRight(2).longValueExact().takeIf { it > 0 && it <= 100_000_000_000L }
} catch (_: Exception) { null }
internal fun validDate(value: String): Boolean = try {
    value.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && LocalDate.parse(value).toString() == value
} catch (_: Exception) { false }

internal fun validSpanishDni(value: String): Boolean {
    val normalized = value.trim().uppercase().replace(" ", "")
    val match = Regex("^([0-9XYZ])(\\d{7})([A-Z])$").matchEntire(normalized) ?: return false
    val prefix = when (match.groupValues[1]) { "X" -> "0"; "Y" -> "1"; "Z" -> "2"; else -> match.groupValues[1] }
    val number = (prefix + match.groupValues[2]).toIntOrNull() ?: return false
    return "TRWAGMYFPDXBNJZSQVHLCKE"[number % 23] == match.groupValues[3][0]
}

private fun formatSsn(value: String): String = value.filter { it.isDigit() }.take(12).chunked(4).joinToString(" ")
private fun formatDateDigits(value: String): String {
    val digits = value.filter { it.isDigit() }.take(8)
    return when { digits.length <= 4 -> digits; digits.length <= 6 -> "${digits.take(4)}-${digits.drop(4)}"; else -> "${digits.take(4)}-${digits.substring(4, 6)}-${digits.drop(6)}" }
}

@Composable
fun HomeEntryForm(kind: String, vm: HomeWorkspaceViewModel, state: HomeWorkspaceState, onBack: () -> Unit,
    editingEmployee: EmpleadoEntity? = null, storage: AttachmentStorage? = null) {
    key(kind, editingEmployee?.id) {
        var title by rememberSaveable { mutableStateOf(editingEmployee?.nombre.orEmpty()) }
        var surname by rememberSaveable { mutableStateOf(editingEmployee?.apellidos.orEmpty()) }
        var trade by rememberSaveable { mutableStateOf(editingEmployee?.oficio.orEmpty()) }
        var type by rememberSaveable { mutableStateOf(editingEmployee?.tipoEmpleado?.let { if (it == "FIJO") "COMPLETO" else it } ?: if (kind == "project-new") "SERVICIO" else "COMPLETO") }
        var dni by rememberSaveable { mutableStateOf(editingEmployee?.dni.orEmpty()) }
        var socialSecurity by rememberSaveable { mutableStateOf(editingEmployee?.numeroSeguridadSocial.orEmpty().filter { it.isDigit() }.take(12)) }
        var residence by rememberSaveable { mutableStateOf(editingEmployee?.lugarResidencia.orEmpty()) }
        var address by rememberSaveable { mutableStateOf(editingEmployee?.direccionPersonal.orEmpty()) }
        var salary by rememberSaveable { mutableStateOf(editingEmployee?.salarioBase?.takeIf { it > 0 }?.toString().orEmpty()) }
        var phone by rememberSaveable { mutableStateOf(editingEmployee?.telefono.orEmpty().filter { it.isDigit() }.take(15)) }
        var email by rememberSaveable { mutableStateOf(editingEmployee?.email.orEmpty()) }
        var client by rememberSaveable { mutableStateOf("") }
        var location by rememberSaveable { mutableStateOf("") }
        var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
        var endDate by rememberSaveable { mutableStateOf("") }
        var amount by rememberSaveable { mutableStateOf("") }
        var rate by rememberSaveable { mutableStateOf("0") }
        var income by rememberSaveable { mutableStateOf(false) }
        var movementCategory by rememberSaveable { mutableStateOf("OTRO") }
        var projectId by rememberSaveable { mutableStateOf<Long?>(null) }
        var employeeId by rememberSaveable { mutableStateOf<Long?>(null) }
        var error by rememberSaveable { mutableStateOf<String?>(null) }
        var attemptedSave by rememberSaveable { mutableStateOf(false) }
        val dniFocus = remember { FocusRequester() }
        val dniBringIntoView = remember { BringIntoViewRequester() }
        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current
        var pendingPdfs by rememberSaveable { mutableStateOf(emptyList<List<String>>()) }
        var pendingDocumentName by rememberSaveable { mutableStateOf("") }
        val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "Documento.pdf"
                val mime = context.contentResolver.getType(uri)
                val header = context.contentResolver.openInputStream(uri)?.use { stream -> ByteArray(5).also { buffer -> stream.read(buffer) } }
                if (mime == "application/pdf" && name.endsWith(".pdf", ignoreCase = true) && header?.toString(Charsets.US_ASCII)?.startsWith("%PDF-") == true && pendingPdfs.none { it[0] == uri.toString() }) {
                    pendingPdfs = pendingPdfs + listOf(listOf(uri.toString(), name, pendingDocumentName.trim()))
                } else error = "Solo se permiten archivos PDF y no se pueden repetir."
            }
        }
        val scrollState = rememberScrollState()
        val saving by vm.saving.collectAsStateWithLifecycle()
        val saveError by vm.saveError.collectAsStateWithLifecycle()
        LaunchedEffect(kind) { vm.saveError.value = null }
        val titleText = when (kind) {
            "employee-new" -> "Alta de empleado"
            "employee-edit" -> "Editar empleado"
            "project-new" -> if (type == "SERVICIO") "Nuevo servicio" else "Nuevo proyecto"
            "movement" -> "Gasto / Ingreso"
            else -> "Parte diario"
        }
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(scrollState).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack, enabled = !saving) { Text("Volver") }
            Text(titleText, style = MaterialTheme.typography.headlineMedium)
            if (state.loading) CircularProgressIndicator()
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); Button(onClick = vm::retry) { Text("Reintentar") } }
            if (kind == "employee-new" || kind == "employee-edit") {
                EntryField("Nombre *", title, { title = it }, !saving)
                EntryField("Apellidos *", surname, { surname = it }, !saving)
                OutlinedTextField(dni, { dni = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(9) },
                    label = { Text("DNI / NIE *") }, singleLine = true, enabled = !saving,
                    isError = attemptedSave && !validSpanishDni(dni),
                    supportingText = { if (attemptedSave && !validSpanishDni(dni)) Text("Comprueba los números y la letra de control del DNI/NIE.") },
                    modifier = Modifier.fillMaxWidth().focusRequester(dniFocus).bringIntoViewRequester(dniBringIntoView))
                TradeSuggestionField(trade, { trade = it }, state.employees.map { e -> e.oficio }, !saving)
                ChoiceField("Jornada", if (type == "FIJO") "COMPLETO" else type, listOf("COMPLETO", "PARCIAL"), !saving) { type = it }
                Text(if (type == "PARCIAL") "Jornada parcial: horario reducido. Registra las horas realizadas desde Parte diario." else "Jornada completa. Registra las horas realizadas desde Parte diario.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                EntryField("Teléfono *", phone, { phone = it }, !saving)
                EntryField("Correo electrónico *", email, { email = it }, !saving)
                EntryField("Salario base mensual (€) *", salary, { salary = it }, !saving)
                OutlinedTextField(value = formatSsn(socialSecurity), onValueChange = { socialSecurity = it.filter { c -> c.isDigit() }.take(12) },
                    label = { Text("Nº de Seguridad Social *") }, placeholder = { Text("0000 0000 0000") }, singleLine = true,
                    enabled = !saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = socialSecurity.length != 12,
                    supportingText = { if (socialSecurity.length != 12) Text("Debe contener exactamente 12 dígitos.") },
                    modifier = Modifier.fillMaxWidth())
                EntryField("Lugar de residencia *", residence, { residence = it }, !saving)
                EntryField("Dirección personal *", address, { address = it }, !saving)
                Text("Documentación laboral", style = MaterialTheme.typography.titleMedium)
                Text("Adjunta contratos, certificados o documentación laboral en PDF. Puedes quitar archivos antes de guardar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                EntryField("Tipo o descripción del documento", pendingDocumentName, { pendingDocumentName = it }, !saving)
                OutlinedButton(enabled = !saving && storage != null, onClick = { pdfPicker.launch(arrayOf("application/pdf")) }) { Text("Seleccionar PDF") }
                pendingPdfs.forEach { item ->
                    val uri = item[0]
                    val name = item[1]
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("${item[2].ifBlank { "Documentación laboral" }} · $name", Modifier.weight(1f))
                        TextButton(enabled = !saving, onClick = { pendingPdfs = pendingPdfs.filterNot { it[0] == uri } }) { Text("Quitar") }
                    }
                }
            } else if (kind == "project-new") {
                ChoiceField("Tipo", type, listOf("SERVICIO", "PROYECTO"), !saving) { type = it }
                Text(if (type == "SERVICIO") "Servicio: actividad o trabajo concreto que realiza la empresa, como limpiar una peluquería cada semana." else "Proyecto: conjunto de trabajos relacionados que se desarrollan durante un periodo, como la reforma integral de un local.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                EntryField(if (type == "SERVICIO") "Nombre del servicio o local *" else "Nombre del proyecto *", title, { title = it }, !saving)
                if (type == "PROYECTO") EntryField("Cliente (opcional)", client, { client = it }, !saving)
                EntryField("Ubicación o dirección *", location, { location = it }, !saving)
                DateEntryField(if (type == "SERVICIO") "Fecha de inicio (AAAA-MM-DD) *" else "Fecha de inicio (AAAA-MM-DD) *", date, { date = it }, !saving, attemptedSave)
                if (type == "PROYECTO") DateEntryField("Fecha de fin prevista (opcional)", endDate, { endDate = it }, !saving, attemptedSave && endDate.isNotBlank())
            } else {
                val available = state.projects.filter { it.estado == "ACTIVO" }
                SelectionField("Servicio / proyecto", projectId, available.map { it.id to it.titulo }, !saving) { projectId = it }
                if (available.isEmpty()) Text("Primero crea un servicio o proyecto desde Inicio.", color = MaterialTheme.colorScheme.secondary)
                DateEntryField("Fecha (AAAA-MM-DD) *", date, { date = it }, !saving, attemptedSave)
                if (kind == "movement") {
                    Text("Ingreso: dinero que recibes por un servicio. Gasto: dinero que pagas, por ejemplo a proveedores. El balance resta gastos a ingresos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    EntryField("Concepto *", title, { title = it }, !saving)
                    EntryField("Importe en euros *", amount, { amount = it }, !saving)
                    ChoiceField("Tipo", if (income) "Ingreso" else "Gasto", listOf("Gasto", "Ingreso"), !saving) { income = it == "Ingreso" }
                    if (!income) ChoiceField("Categoría", movementCategory, listOf("MATERIALES", "PERSONAL", "TRANSPORTE", "SUMINISTROS", "OTRO"), !saving) { movementCategory = it }
                } else {
                    SelectionField("Empleado", employeeId, state.employees.filter { it.activo }.map { it.id to "${it.nombre} ${it.apellidos}" }, !saving) { employeeId = it }
                    if (state.employees.isEmpty()) Text("Primero da de alta un empleado desde Inicio.", color = MaterialTheme.colorScheme.secondary)
                    EntryField("Horas trabajadas *", amount, { amount = it }, !saving)
                    EntryField("Coste por hora en euros (0 si no se conoce)", rate, { rate = it }, !saving)
                }
            }
            (error ?: saveError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !saving && !state.loading && state.error == null,
                onClick = {
                    error = null
                    attemptedSave = true
                    when (kind) {
                        "employee-new", "employee-edit" -> {
                            if (!validPersonName(title) || !validPersonName(surname)) error = "El nombre y los apellidos solo pueden contener letras, espacios, guiones o apóstrofos."
                    else if (!validSpanishDni(dni)) {
                        error = "El DNI/NIE no es válido: revisa su formato y la letra de control."
                        attemptedSave = true
                        coroutineScope.launch { runCatching { dniFocus.requestFocus(); dniBringIntoView.bringIntoView() } }
                    }
                            else if (trade.isBlank()) error = "Indica el oficio del empleado."
                            else if (phone.length !in 9..15) error = "El teléfono debe tener entre 9 y 15 dígitos."
                            else if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) error = "Introduce un correo electrónico válido."
                            else if (salary.isBlank() || (salary.replace(',', '.').toDoubleOrNull()?.let { !it.isFinite() || it < 0 } != false)) error = "Introduce un salario base válido."
                            else if (residence.isBlank() || address.isBlank()) error = "Completa el lugar de residencia y la dirección personal."
                    else if (socialSecurity.length != 12) error = "El número de Seguridad Social es obligatorio y debe tener 12 dígitos."
                            else {
                                val salaryValue = salary.replace(',', '.').toDouble()
                                val value = EmpleadoEntity(id = editingEmployee?.id ?: 0, nombre = title.trim(), apellidos = surname.trim(), dni = dni.trim(),
                                    telefono = phone.trim(), email = email.trim(), oficio = trade.trim(), tipoEmpleado = type, salarioBase = salaryValue,
                                    lugarResidencia = residence.trim(), direccionPersonal = address.trim(), fotoUrl = editingEmployee?.fotoUrl,
                                    activo = editingEmployee?.activo ?: true, numeroSeguridadSocial = socialSecurity)
                                vm.save({
                                    val employeeId = if (editingEmployee == null) addEmployee(value) else { updateEmployee(value); editingEmployee.id.toLong() }
                                    pendingPdfs.forEach { item ->
                                        val uriText = item[0]
                                        val fileName = item[1]
                                        val uri = Uri.parse(uriText)
                                        val stored = requireNotNull(storage).importUri(uri, "empleado")
                                        addAttachment(HomeAttachment(ownerKind = "EMPLEADO", ownerId = employeeId, type = "DOCUMENTO",
                                            category = "LABORAL", name = "${item[2].ifBlank { "Documentación laboral" }} · $fileName",
                                            path = stored.path, mimeType = "application/pdf", createdAt = LocalDate.now().toString()))
                                    }
                                }, onBack)
                            }
                        }
                        "project-new" -> {
                            if (title.isBlank() || location.isBlank()) error = "Completa el nombre y la ubicación."
                            else if (!validDate(date) || (endDate.isNotBlank() && (!validDate(endDate) || endDate < date))) error = "Revisa las fechas; el fin no puede ser anterior al inicio."
                            else vm.save({ addProject(UnidadTrabajoEntity(titulo = title.trim(), tipo = type,
                                clienteNombre = if (type == "PROYECTO") client.trim() else "", direccionUbicacion = location.trim(), fechaInicio = date,
                                fechaFin = if (type == "PROYECTO") endDate.ifBlank { null } else null)) }, onBack)
                        }
                        else -> {
                            val p = projectId
                            val e = employeeId
                            val numeric = positiveNumber(amount)
                            val cents = moneyCents(amount)
                            val hourly = rate.replace(',', '.').toDoubleOrNull()
                            if (p == null || state.projects.none { it.id == p && it.estado == "ACTIVO" }) error = "Selecciona un servicio o proyecto activo."
                            else if (!validDate(date)) error = "Introduce una fecha válida con formato AAAA-MM-DD."
                            else if (kind == "movement") {
                                if (title.isBlank() || cents == null) error = "Indica el concepto y un importe positivo con un máximo de dos decimales."
                                else vm.save({ addMovement(HomeMovement(projectId = p, concept = title.trim(), amountCents = cents, income = income, date = date, category = if (income) "INGRESO" else movementCategory)) }, onBack)
                            } else if (e == null || state.employees.none { it.id == e && it.activo }) error = "Selecciona un empleado activo."
                            else if (numeric == null || numeric > 24 || hourly == null || !hourly.isFinite() || hourly < 0 || hourly > 1_000_000) error = "Las horas deben estar entre 0 y 24 y el coste por hora no puede ser negativo."
                            else if (date > LocalDate.now().toString()) error = "No se pueden registrar horas trabajadas en una fecha futura."
                            else if (state.hours.filter { it.empleadoId == e && it.fecha == date }.sumOf { it.horasTrabajadas } + numeric > 24) error = "Este empleado superaría las 24 horas registradas en el día."
                            else vm.save({ addHours(RegistroHorasEntity(unidadTrabajoId = p, empleadoId = e, fecha = date, horasTrabajadas = numeric, costeHoraAplicado = hourly)) }, onBack)
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "Guardando…" else "Guardar") }
            if (kind == "movement") {
                Text("Últimos movimientos", style = MaterialTheme.typography.titleMedium)
                state.movements.take(20).forEach { movement ->
                    Text("${movement.date} · ${movement.concept} · ${if (movement.income) "Ingreso" else "Gasto · ${movement.category}"} · ${if (movement.income) "+" else "−"}${"%.2f".format(movement.amountCents / 100.0)} €")
                }
            } else if (kind == "daily") {
                Text("Últimos partes", style = MaterialTheme.typography.titleMedium)
                state.hours.take(20).forEach { hours ->
                    val employee = state.employees.firstOrNull { it.id == hours.empleadoId }
                    val project = state.projects.firstOrNull { it.id == hours.unidadTrabajoId }
                    Text("${hours.fecha} · ${employee?.nombre.orEmpty()} · ${project?.titulo.orEmpty()} · ${hours.horasTrabajadas} h")
                }
            }
        }
    }
}

@Composable
internal fun EntryField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean) {
    val isPhone = label.contains("teléfono", ignoreCase = true)
    val isSocialSecurity = label.contains("Seguridad Social", ignoreCase = true)
    val isPersonName = label == "Nombre *" || label == "Apellidos *"
    OutlinedTextField(value, { input ->
        val clean = when {
            isPhone || isSocialSecurity -> input.filter { it.isDigit() }.take(if (isPhone) 15 else 12)
            isPersonName -> input.filter { it.isLetter() || it.isWhitespace() || it == '-' || it == '\'' }
            else -> input
        }
        onChange(clean)
    }, label = { Text(label) }, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = when {
            isPhone || isSocialSecurity -> KeyboardType.Number
            label.contains("correo", ignoreCase = true) -> KeyboardType.Email
            label.contains("salario", ignoreCase = true) || label.contains("importe", ignoreCase = true) || label.contains("euros", ignoreCase = true) -> KeyboardType.Decimal
            else -> KeyboardType.Text
        }))
}

@Composable
private fun TradeSuggestionField(value: String, onChange: (String) -> Unit, previousTrades: List<String>, enabled: Boolean = true) {
    val suggestions = previousTrades.map(String::trim).filter(String::isNotBlank)
        .distinctBy { it.normalizedSearch() }
        .filter { value.isBlank() || it.normalizedSearch().contains(value.normalizedSearch()) }
        .take(8)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EntryField("Oficio *", value, onChange, enabled)
        if (suggestions.isNotEmpty()) {
            Text("Oficios anteriores", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { option -> AssistChip(onClick = { onChange(option) }, label = { Text(option) }) }
            }
        }
    }
}

@Composable
internal fun DateEntryField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean, showError: Boolean = false) {
    val invalid = (showError && value.isBlank()) || (value.isNotBlank() && !validDate(value))
    OutlinedTextField(value = value, onValueChange = { input ->
        val digits = input.filter { it.isDigit() }.take(8)
        val oldDigits = value.filter { it.isDigit() }
        val adjusted = if (input.length < value.length && digits.length == oldDigits.length) digits.dropLast(1) else digits
        onChange(formatDateDigits(adjusted))
    }, label = { Text(label) }, placeholder = { Text("AAAA-MM-DD") }, singleLine = true, enabled = enabled,
        isError = invalid && showError,
        supportingText = { if (invalid && showError) Text("Introduce una fecha real con formato AAAA-MM-DD.") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
}

private fun validPersonName(value: String): Boolean = value.trim().matches(Regex("[\\p{L}][\\p{L} .'-]{0,99}"))

@Composable
internal fun ChoiceField(label: String, value: String, options: List<String>, enabled: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("$label: $value") }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false }) }
        }
    }
}

@Composable
internal fun SelectionField(label: String, selected: Long?, options: List<Pair<Long, String>>, enabled: Boolean, onChange: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled && options.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${options.firstOrNull { it.first == selected }?.second ?: "Seleccionar"}")
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onChange(id); expanded = false }) }
        }
    }
}
