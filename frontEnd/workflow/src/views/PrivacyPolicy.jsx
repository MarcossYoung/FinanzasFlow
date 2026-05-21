import React from 'react';

export default function PrivacyPolicy() {
	return (
		<div style={{maxWidth: '800px', margin: '0 auto', padding: '40px 20px', fontFamily: 'system-ui, -apple-system, sans-serif', color: '#2d3436', lineHeight: '1.7'}}>
			<h1 style={{fontSize: '2rem', marginBottom: '8px'}}>Politica de Privacidad</h1>
			<p style={{color: '#636e72', marginBottom: '32px'}}>Ultima actualizacion: marzo 2026</p>

			<section style={{marginBottom: '28px'}}>
				<h2 style={{fontSize: '1.2rem', fontWeight: '600', color: '#2d3436', marginBottom: '8px'}}>1. Informacion que recopilamos</h2>
				<p>FinanzasFlow recopila la informacion que usted proporciona al registrarse y utilizar el sistema, incluyendo:</p>
				<ul>
					<li>Datos de cuenta: nombre, email y rol de usuario.</li>
					<li>Datos de clientes: nombre, CUIT/DNI, telefono, email y notas comerciales.</li>
					<li>Datos de facturas: titulo, fechas, importes, estado de gestion y notas.</li>
					<li>Informacion de pagos asociada a cada factura.</li>
				</ul>
			</section>

			<section style={{marginBottom: '28px'}}>
				<h2 style={{fontSize: '1.2rem', fontWeight: '600', color: '#2d3436', marginBottom: '8px'}}>2. Como usamos su informacion</h2>
				<ul>
					<li>Gestionar facturas, cobranzas y seguimiento financiero.</li>
					<li>Mostrar tableros, reportes y proyecciones de flujo de caja.</li>
					<li>Enviar recordatorios o notificaciones operativas, si estan configuradas.</li>
					<li>Mejorar la seguridad y administracion del servicio.</li>
				</ul>
			</section>

			<section style={{marginBottom: '28px'}}>
				<h2 style={{fontSize: '1.2rem', fontWeight: '600', color: '#2d3436', marginBottom: '8px'}}>3. Almacenamiento y seguridad</h2>
				<p>Aplicamos controles razonables para proteger los datos contra accesos no autorizados, perdida o alteracion. El acceso esta limitado por roles y autenticacion.</p>
			</section>

			<section style={{marginBottom: '28px'}}>
				<h2 style={{fontSize: '1.2rem', fontWeight: '600', color: '#2d3436', marginBottom: '8px'}}>4. Compartir informacion con terceros</h2>
				<p>No vendemos informacion personal. Podemos usar proveedores tecnicos para hosting, almacenamiento, analitica, automatizaciones o mensajeria, siempre vinculados a la operacion del servicio.</p>
			</section>

			<section style={{marginBottom: '28px'}}>
				<h2 style={{fontSize: '1.2rem', fontWeight: '600', color: '#2d3436', marginBottom: '8px'}}>5. Sus derechos</h2>
				<p>Puede solicitar acceso, rectificacion o eliminacion de sus datos, sujeto a obligaciones legales o comerciales aplicables.</p>
			</section>

			<section style={{marginBottom: '28px'}}>
				<h2 style={{fontSize: '1.2rem', fontWeight: '600', color: '#2d3436', marginBottom: '8px'}}>6. Contacto</h2>
				<p>Para consultas sobre privacidad, contacte al administrador de su organizacion.</p>
			</section>

			<p style={{fontSize: '0.85rem', color: '#b2bec3', marginTop: '40px', borderTop: '1px solid #dfe6e9', paddingTop: '20px'}}>
				FinanzasFlow - B2B Financial Workspace
			</p>
		</div>
	);
}
