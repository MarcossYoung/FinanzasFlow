import React, {useCallback, useEffect, useState} from 'react';
import axios from 'axios';
import {FaEdit, FaPlus, FaPowerOff, FaSave, FaTimes} from 'react-icons/fa';
import {BASE_URL} from '../api/config';

const emptyForm = {
	name: '',
	email: '',
	phone: '',
	adminUsername: '',
	adminPassword: '',
};

export default function OperatorPage() {
	const [tenants, setTenants] = useState([]);
	const [activity, setActivity] = useState([]);
	const [selectedTenant, setSelectedTenant] = useState(null);
	const [loading, setLoading] = useState(true);
	const [activityLoading, setActivityLoading] = useState(false);
	const [saving, setSaving] = useState(false);
	const [error, setError] = useState('');
	const [formMode, setFormMode] = useState('create');
	const [form, setForm] = useState(emptyForm);

	const authHeaders = () => ({Authorization: `Bearer ${localStorage.getItem('token')}`});

	const fetchTenants = useCallback(async () => {
		try {
			const res = await axios.get(`${BASE_URL}/api/operator/tenants`, {
				headers: authHeaders(),
			});
			setTenants(res.data);
			setError('');
		} catch {
			setError('Error al cargar tenants.');
		} finally {
			setLoading(false);
		}
	}, []);

	const fetchActivity = useCallback(async (tenantId) => {
		setActivityLoading(true);
		try {
			const res = await axios.get(`${BASE_URL}/api/operator/tenants/${tenantId}/activity`, {
				headers: authHeaders(),
			});
			setActivity(res.data);
		} catch {
			setActivity([]);
		} finally {
			setActivityLoading(false);
		}
	}, []);

	useEffect(() => { fetchTenants(); }, [fetchTenants]);

	const handleSelectTenant = (tenant) => {
		setSelectedTenant(tenant);
		fetchActivity(tenant.id);
	};

	const startCreate = () => {
		setFormMode('create');
		setForm(emptyForm);
	};

	const startEdit = (tenant) => {
		setSelectedTenant(tenant);
		setFormMode('edit');
		setForm({
			name: tenant.name ?? '',
			email: tenant.email ?? '',
			phone: tenant.phone ?? '',
			adminUsername: '',
			adminPassword: '',
		});
		fetchActivity(tenant.id);
	};

	const handleChange = (event) => {
		const {name, value} = event.target;
		setForm((current) => ({...current, [name]: value}));
	};

	const handleSubmit = async (event) => {
		event.preventDefault();
		setSaving(true);
		try {
			if (formMode === 'edit' && selectedTenant) {
				await axios.put(`${BASE_URL}/api/operator/tenants/${selectedTenant.id}`, {
					name: form.name,
					email: form.email,
					phone: form.phone,
				}, {headers: authHeaders()});
			} else {
				await axios.post(`${BASE_URL}/api/operator/tenants`, form, {headers: authHeaders()});
			}
			await fetchTenants();
			startCreate();
		} catch {
			setError('No se pudo guardar el tenant.');
		} finally {
			setSaving(false);
		}
	};

	const toggleActive = async (tenant) => {
		try {
			await axios.put(`${BASE_URL}/api/operator/tenants/${tenant.id}/active`, {
				active: !tenant.active,
			}, {headers: authHeaders()});
			await fetchTenants();
			if (selectedTenant?.id === tenant.id) {
				setSelectedTenant({...tenant, active: !tenant.active});
			}
		} catch {
			setError('No se pudo cambiar el estado del tenant.');
		}
	};

	const formatDateTime = (value) => {
		if (!value) return '-';
		return new Intl.DateTimeFormat('es-AR', {
			year: 'numeric',
			month: '2-digit',
			day: '2-digit',
			hour: '2-digit',
			minute: '2-digit',
		}).format(new Date(value));
	};

	if (loading) return <div className='operator-state'>Cargando...</div>;

	return (
		<div className='operator-page'>
			<div className='operator-title-row'>
				<h1 className='page-title'>Panel Operador</h1>
				<button type='button' className='operator-icon-button' onClick={startCreate} title='Nuevo tenant'>
					<FaPlus aria-hidden='true' />
					<span>Nuevo</span>
				</button>
			</div>
			{error && <p className='operator-error' role='alert'>{error}</p>}

			<form className='operator-form' onSubmit={handleSubmit}>
				<div className='operator-form-header'>
					<h2>{formMode === 'edit' ? 'Editar tenant' : 'Crear tenant'}</h2>
					{formMode === 'edit' && (
						<button type='button' className='operator-icon-button secondary' onClick={startCreate} title='Cancelar edicion'>
							<FaTimes aria-hidden='true' />
							<span>Cancelar</span>
						</button>
					)}
				</div>
				<div className='operator-form-grid'>
					<label>Nombre<input name='name' value={form.name} onChange={handleChange} required /></label>
					<label>Email<input name='email' value={form.email} onChange={handleChange} type='email' /></label>
					<label>Telefono<input name='phone' value={form.phone} onChange={handleChange} /></label>
					{formMode === 'create' && (
						<>
							<label>Admin<input name='adminUsername' value={form.adminUsername} onChange={handleChange} required /></label>
							<label>Clave<input name='adminPassword' value={form.adminPassword} onChange={handleChange} type='password' minLength={8} required /></label>
						</>
					)}
				</div>
				<button type='submit' className='operator-icon-button' disabled={saving} title='Guardar tenant'>
					<FaSave aria-hidden='true' />
					<span>{saving ? 'Guardando' : 'Guardar'}</span>
				</button>
			</form>

			{tenants.length === 0 ? <p className='operator-state'>No hay tenants disponibles.</p> : (
				<div className='operator-tenant-grid'>
					{tenants.map((tenant) => (
						<div
							key={tenant.id}
							className={`operator-tenant-card${selectedTenant?.id === tenant.id ? ' operator-tenant-card-active' : ''}`}
						>
							<button type='button' className='operator-tenant-main' onClick={() => handleSelectTenant(tenant)}>
								<div className='operator-tenant-header'>
									<div><strong>{tenant.name}</strong><span>ID: {tenant.id}</span></div>
									<span className={`operator-tenant-badge ${tenant.active ? 'active' : 'inactive'}`}>
										{tenant.active ? 'Activo' : 'Inactivo'}
									</span>
								</div>
								<div className='operator-metric-grid'>
									<div><span>Usuarios</span><strong>{tenant.userCount}</strong></div>
									<div><span>Acciones este mes</span><strong>{tenant.actionsThisMonth}</strong></div>
									<div><span>Ultima actividad</span><strong>{formatDateTime(tenant.lastActivityAt)}</strong></div>
									<div><span>Creado</span><strong>{formatDateTime(tenant.createdAt)}</strong></div>
								</div>
							</button>
							<div className='operator-card-actions'>
								<button type='button' onClick={() => startEdit(tenant)} title='Editar tenant'>
									<FaEdit aria-hidden='true' /><span>Editar</span>
								</button>
								<button type='button' onClick={() => toggleActive(tenant)} title={tenant.active ? 'Desactivar tenant' : 'Activar tenant'}>
									<FaPowerOff aria-hidden='true' /><span>{tenant.active ? 'Desactivar' : 'Activar'}</span>
								</button>
							</div>
						</div>
					))}
				</div>
			)}

			{selectedTenant && <section className='operator-action-section'>
				<h2>Actividad reciente - <span>{selectedTenant.name}</span></h2>
				{activityLoading ? <p className='operator-state'>Cargando actividad...</p> : activity.length === 0 ? (
					<p className='operator-state'>Sin actividad registrada.</p>
				) : (
					<div className='operator-action-table-wrapper'>
						<table className='operator-action-table'>
							<thead><tr><th>Tipo</th><th>Fecha</th></tr></thead>
							<tbody>{activity.map((item) => <tr key={item.id}>
								<td>{item.actionType}</td><td>{formatDateTime(item.createdAt)}</td>
							</tr>)}</tbody>
						</table>
					</div>
				)}
			</section>}
		</div>
	);
}
