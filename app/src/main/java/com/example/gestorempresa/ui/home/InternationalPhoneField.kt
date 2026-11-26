package com.example.gestorempresa.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.domain.phone.InternationalPhones
import com.example.gestorempresa.domain.phone.PhoneInput

@Composable
internal fun InternationalPhoneField(
    region: String,
    number: String,
    onChange: (PhoneInput) -> Unit,
    enabled: Boolean,
    attemptedSave: Boolean,
    modifier: Modifier = Modifier,
    required: Boolean = true,
    tagPrefix: String = "employee-phone",
    inline: Boolean = false
) {
    var choosingCountry by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val country = InternationalPhones.country(region)
    val invalid = attemptedSave && (required || number.isNotBlank()) && InternationalPhones.toE164(number, region) == null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("País y prefijo telefónico", style = MaterialTheme.typography.labelMedium)
        @Composable fun countryButton(buttonModifier: Modifier) {
            OutlinedButton(onClick = { query = ""; choosingCountry = true }, enabled = enabled,
                modifier = buttonModifier.testTag("${tagPrefix}-country")
                    .semantics { contentDescription = "País y prefijo telefónico: ${country.name}" }) {
                Text(if (inline) "${country.flag} +${country.callingCode}" else country.label)
            }
        }
        @Composable fun numberField(numberModifier: Modifier) {
          OutlinedTextField(value = number, onValueChange = { onChange(InternationalPhones.edit(it, region)) },
            label = { Text(if (required) "Teléfono *" else "Número (opcional)") }, singleLine = true, enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = invalid,
            supportingText = {
                Text(if (invalid) "Introduce un teléfono válido para ${country.name} (+${country.callingCode}), sin extensiones."
                    else "Escribe el número o pega uno con prefijo internacional.")
            }, modifier = numberModifier.then(modifier).fillMaxWidth().testTag("${tagPrefix}-number"))
        }
        if (inline) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            countryButton(Modifier.widthIn(min = 100.dp))
            numberField(Modifier.weight(1f))
        } else {
            countryButton(Modifier.fillMaxWidth())
            numberField(Modifier)
        }
    }
    if (choosingCountry) AlertDialog(
        onDismissRequest = { choosingCountry = false },
        title = { Text("Selecciona el país") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Buscar país o prefijo") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("phone-country-search"))
                val matches = remember(query) { InternationalPhones.search(query) }
                if (matches.isEmpty()) Text("No hay países que coincidan.")
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(matches, key = { it.region }) { option ->
                        TextButton(onClick = { onChange(PhoneInput(option.region, number)); choosingCountry = false },
                            modifier = Modifier.fillMaxWidth().testTag("phone-country-${option.region}")
                                .semantics { selected = option.region == region }) { Text(option.label) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosingCountry = false }) { Text("Cancelar") } }
    )
}
