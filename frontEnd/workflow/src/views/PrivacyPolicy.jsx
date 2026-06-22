import React from 'react';

export default function PrivacyPolicy() {
	return (
		<main className='privacy-policy'>
			<h1>Política de Privacidad</h1>
			<p className='privacy-policy-updated'>Última actualización: marzo 2026</p>
			<section><h2>1. Información que recopilamos</h2>
				<p>FinanzasFlow recopila la información que usted proporciona al registrarse y utilizar el sistema, incluyendo:</p>
				<ul><li>Datos de cuenta: nombre, email y rol de usuario.</li><li>Datos de clientes: nombre, CUIT/DNI, teléfono, email y notas comerciales.</li><li>Datos de facturas: título, fechas, importes, estado de gestión y notas.</li><li>Información de pagos asociada a cada factura.</li></ul>
			</section>
			<section><h2>2. Cómo usamos su información</h2>
				<ul><li>Gestionar facturas, cobranzas y seguimiento financiero.</li><li>Mostrar tableros, reportes y proyecciones de flujo de caja.</li><li>Enviar recordatorios o notificaciones operativas, si están configuradas.</li><li>Mejorar la seguridad y administración del servicio.</li></ul>
			</section>
			<section><h2>3. Almacenamiento y seguridad</h2><p>Aplicamos controles razonables para proteger los datos contra accesos no autorizados, pérdida o alteración. El acceso está limitado por roles y autenticación.</p></section>
			<section><h2>4. Compartir información con terceros</h2><p>No vendemos información personal. Podemos usar proveedores técnicos para hosting, almacenamiento, analítica, automatizaciones o mensajería, siempre vinculados a la operación del servicio.</p></section>
			<section><h2>5. Sus derechos</h2><p>Puede solicitar acceso, rectificación o eliminación de sus datos, sujeto a obligaciones legales o comerciales aplicables.</p></section>
			<section><h2>6. Contacto</h2><p>Para consultas sobre privacidad, contacte al administrador de su organización.</p></section>
			<p className='privacy-policy-footer'>FinanzasFlow - B2B Financial Workspace</p>
		</main>
	);
}
