import { readFile } from 'node:fs/promises';
import { test, after } from 'node:test';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, setDoc, getDoc, updateDoc, collection, getDocs, deleteDoc } from 'firebase/firestore';

const env = await initializeTestEnvironment({
  projectId: 'demo-gestor-empresa',
  firestore: { rules: await readFile(new URL('../../firestore.rules', import.meta.url), 'utf8') }
});
after(() => env.cleanup());
const a = env.authenticatedContext('usuario-a').firestore();
const b = env.authenticatedContext('usuario-b').firestore();
const anon = env.unauthenticatedContext().firestore();
const data = { nombre: 'Ana', apellidos: 'Pérez', empresaId: 'usuario-a', empresaNombre: 'Empresa', sector: 'Limpieza', creadaEn: 1, telefono: '', dni: '', cargo: '' };
test('propietario crea y recupera su perfil desde otra sesión', async () => {
  await assertSucceeds(setDoc(doc(a, 'authProfiles/usuario-a'), data));
  const segundo = env.authenticatedContext('usuario-a').firestore();
  await assertSucceeds(getDoc(doc(segundo, 'authProfiles/usuario-a')));
});
test('otro usuario y anónimo no pueden leer ni modificar el perfil', async () => {
  for (const db of [b, anon]) {
    await assertFails(getDoc(doc(db, 'authProfiles/usuario-a')));
    await assertFails(setDoc(doc(db, 'authProfiles/usuario-a'), data));
  }
});
test('no admite empresa ajena, cambio de identidad ni campos de credenciales o roles', async () => {
  await assertFails(setDoc(doc(b, 'authProfiles/usuario-b'), data));
  await assertFails(updateDoc(doc(a, 'authProfiles/usuario-a'), { empresaId: 'usuario-b' }));
  await assertFails(updateDoc(doc(a, 'authProfiles/usuario-a'), { creadaEn: 2 }));
  for (const key of ['password', 'passwordHash', 'rol']) {
    await assertFails(updateDoc(doc(a, 'authProfiles/usuario-a'), { [key]: 'valor' }));
  }
});
test('valida datos y permite editar solo el perfil propio', async () => {
  await assertFails(updateDoc(doc(a, 'authProfiles/usuario-a'), { nombre: '' }));
  await assertFails(updateDoc(doc(a, 'authProfiles/usuario-a'), { sector: 'Inventado' }));
  await assertSucceeds(updateDoc(doc(a, 'authProfiles/usuario-a'), { nombre: 'Andrea', telefono: '+34 600123456' }));
});
test('nadie lista perfiles ni borra la cuenta desde este contrato', async () => {
  await assertFails(getDocs(collection(a, 'authProfiles')));
  await assertFails(deleteDoc(doc(a, 'authProfiles/usuario-a')));
});
test('direccion y pais del telefono pertenecen solo al propietario y validan limites', async () => {
  await assertSucceeds(updateDoc(doc(a, 'authProfiles/usuario-a'), { direccion: 'Calle Mayor 10', telefonoRegion: 'GB' }));
  await assertFails(updateDoc(doc(b, 'authProfiles/usuario-a'), { direccion: 'Ajena' }));
  await assertFails(updateDoc(doc(a, 'authProfiles/usuario-a'), { direccion: 'x'.repeat(241) }));
  await assertFails(updateDoc(doc(a, 'authProfiles/usuario-a'), { telefonoRegion: 'pais-inventado' }));
});
