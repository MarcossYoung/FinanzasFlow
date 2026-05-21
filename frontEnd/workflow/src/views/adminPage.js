import React, {useEffect, useState, useCallback} from 'react';
import axios from 'axios';
import {
	FaUser,
	FaClipboardList,
	FaClock,
	FaCheckCircle,
	FaTrashAlt,
	FaUserPlus,
	FaEdit,
	FaCheck,
	FaTimes,
} from 'react-icons/fa';
import {
	BarChart,
	Bar,
	XAxis,
	YAxis,
	Tooltip,
	ResponsiveContainer,
	CartesianGrid,
} from 'recharts';
import {BASE_URL} from '../api/config';

function AdminPage() {
	// --- STATES ---
	const [summary, setSummary] = useState({
		totalUsers: 0,
		totalOrders: 0,
		finishedOrders: 0,
		dueThisWeek: 0,
	});
	const [digest, setDigest] = useState('');
	const [chartData, setChartData] = useState([]);

	// User Management States
	const [users, setUsers] = useState([]);
	const [showUserModal, setShowUserModal] = useState(false);
	const [newUser, setNewUser] = useState({
		username: '',
		password: '',
		appUserRole: 'GESTOR',
	});
	const [msg, setMsg] = useState({text: '', type: ''}); // Unified message state
	const [editingUserId, setEditingUserId] = useState(null);
	const [editingRole, setEditingRole] = useState('');

	// --- FETCH DATA ---
	const fetchData = useCallback(async () => {
		try {
			const token = localStorage.getItem('token');
			const config = {headers: {Authorization: `Bearer ${token}`}};

			// 1. Fetch Summary Stats
			const summaryRes = await axios.get(
				`${BASE_URL}/api/admin/summary`,
				config,
			);
			setSummary(summaryRes.data);
			setChartData([
				{name: 'Total Facturas', value: summaryRes.data.totalOrders},
				{name: 'Cerradas', value: summaryRes.data.finishedOrders},
				{name: 'Para esta semana', value: summaryRes.data.dueThisWeek},
			]);

			// 2. Fetch Users List (Nuevo)
			// Si tu endpoint es diferente, cambialo aqui.
			// Generalmente es /api/users o /api/admin/users
			const usersRes = await axios.get(`${BASE_URL}/api/admin/users`, config);
			setUsers(usersRes.data);
		} catch (err) {
			console.error('Error fetching admin data:', err);
		}
	}, []);

	useEffect(() => {
		fetchData();
	}, [fetchData]);

	// Weekly digest: fetch once per week
	useEffect(() => {
		const now = new Date();
		const year = now.getFullYear();
		const week = Math.ceil(((now - new Date(year, 0, 1)) / 86400000 + new Date(year, 0, 1).getDay() + 1) / 7);
		const weekKey = `${year}-W${String(week).padStart(2, '0')}`;
		const stored = localStorage.getItem('digest_week');
		if (stored === weekKey) return; // Already shown this week
		const token = localStorage.getItem('token');
		axios.get(`${BASE_URL}/api/ai/weekly-digest`, {headers: {Authorization: `Bearer ${token}`}})
			.then(res => setDigest(res.data.digest))
			.catch(() => {});
	}, []);

	// --- HANDLERS ---
	const handleCreateUser = async (e) => {
		e.preventDefault();
		try {
			const token = localStorage.getItem('token');
			await axios.post(`${BASE_URL}/api/users/registro`, newUser, {
				headers: {Authorization: `Bearer ${token}`},
			});

			setMsg({text: 'Usuario creado con éxito', type: 'green'});
			setNewUser({username: '', password: '', appUserRole: 'GESTOR'});
			setShowUserModal(false);
			fetchData(); // Recargar la lista

			setTimeout(() => setMsg({text: '', type: ''}), 3000);
		} catch (error) {
			console.error('Error creando usuario:', error);
			setMsg({text: 'Error al crear usuario', type: 'red'});
		}
	};

	const handleEditRole = async (id) => {
		try {
			const token = localStorage.getItem('token');
			await axios.put(
				`${BASE_URL}/api/admin/users/${id}/role`,
				{appUserRole: editingRole},
				{headers: {Authorization: `Bearer ${token}`}},
			);
			setMsg({text: 'Rol actualizado', type: 'green'});
			setEditingUserId(null);
			fetchData();
			setTimeout(() => setMsg({text: '', type: ''}), 3000);
		} catch {
			setMsg({text: 'Error al actualizar rol', type: 'red'});
		}
	};

	const handleDeleteUser = async (id) => {
		if (!window.confirm('¿Estás seguro de eliminar este usuario?')) return;
		try {
			const token = localStorage.getItem('token');
			await axios.delete(`${BASE_URL}/api/admin/users/${id}`, {
				headers: {Authorization: `Bearer ${token}`},
			});
			fetchData();
		} catch (error) {
			alert('No se pudo eliminar el usuario');
		}
	};

	const dismissDigest = () => {
		const now = new Date();
		const year = now.getFullYear();
		const week = Math.ceil(((now - new Date(year, 0, 1)) / 86400000 + new Date(year, 0, 1).getDay() + 1) / 7);
		localStorage.setItem('digest_week', `${year}-W${String(week).padStart(2, '0')}`);
		setDigest('');
	};

	return (
		<div className='admin-dashboard'>
			{/* WEEKLY AI DIGEST BANNER */}
			{digest && (
				<div style={{
					background: 'linear-gradient(135deg, #6c5ce7, #a29bfe)',
					color: 'white',
					borderRadius: '12px',
					padding: '16px 20px',
					marginBottom: '1.5rem',
					display: 'flex',
					justifyContent: 'space-between',
					alignItems: 'flex-start',
					gap: '12px',
					boxShadow: '0 4px 12px rgba(108,92,231,0.3)',
				}}>
					<div>
						<strong style={{fontSize: '0.85rem', opacity: 0.85}}>Resumen Semanal IA</strong>
						<p style={{margin: '4px 0 0', fontSize: '0.95rem', lineHeight: '1.5'}}>{digest}</p>
					</div>
					<button
						onClick={dismissDigest}
						style={{background: 'transparent', border: 'none', color: 'white', cursor: 'pointer', fontSize: '1.2rem', flexShrink: 0, padding: '0 4px'}}
						title='Cerrar'
					>
						×
					</button>
				</div>
			)}

			{/* 1. HEADER & KPI CARDS */}
			<div className='dashboard-header' style={{marginBottom: '2rem'}}>
				<h1 className='main-title'>Panel de Administración</h1>

				<div className='dashboard-cards'>
					<div className='card'>
						<FaUser className='card-icon' />
						<div className='card-info'>
							<h3>Usuarios</h3>
							<p>{summary.totalUsers}</p>
						</div>
					</div>
					<div className='card'>
						<FaClipboardList className='card-icon' />
						<div className='card-info'>
							<h3>Facturas Totales</h3>
							<p>{summary.totalOrders}</p>
						</div>
					</div>
					<div className='card'>
						<FaClock className='card-icon' />
						<div className='card-info'>
							<h3>Para esta Semana</h3>
							<p>{summary.dueThisWeek}</p>
						</div>
					</div>
					<div className='card'>
						<FaCheckCircle className='card-icon' />
						<div className='card-info'>
							<h3>Cerradas</h3>
							<p>{summary.finishedOrders}</p>
						</div>
					</div>
				</div>
			</div>

			{/* 2. MAIN GRID: CHART & USERS */}
			{/* Usamos un grid simple para separar visualmente si hay espacio */}
			<div
				style={{display: 'flex', flexDirection: 'column', gap: '2rem'}}
			>
				{/* SECTION A: CHART */}
				<div className='costs-wrapper' style={{marginTop: 0}}>
					<h2 style={{marginBottom: '1rem'}}>Resumen de Actividad</h2>
					<div style={{width: '100%', height: 300}}>
						<ResponsiveContainer>
							<BarChart
								data={chartData}
								margin={{
									top: 20,
									right: 30,
									left: 0,
									bottom: 5,
								}}
							>
								<CartesianGrid
									strokeDasharray='3 3'
									vertical={false}
								/>
								<XAxis dataKey='name' />
								<YAxis />
								<Tooltip
									contentStyle={{
										borderRadius: '8px',
										border: 'none',
										boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
									}}
								/>
								<Bar
									dataKey='value'
									fill='#00b894'
									radius={[4, 4, 0, 0]}
									barSize={50}
								/>
							</BarChart>
						</ResponsiveContainer>
					</div>
				</div>

				{/* SECTION B: USER MANAGEMENT */}
				<div className='costs-wrapper' style={{marginTop: 0}}>
					<div
						className='flex justify-between align-center'
						style={{
							marginBottom: '1.5rem',
							display: 'flex',
							justifyContent: 'space-between',
						}}
					>
						<h2>Gestión de Usuarios</h2>
						<button
							className='button_3'
							onClick={() => setShowUserModal(true)}
							style={{
								display: 'flex',
								alignItems: 'center',
								gap: '8px',
							}}
						>
							<FaUserPlus /> Nuevo Usuario
						</button>
					</div>

					{/* Feedback Messages */}
					{msg.text && (
						<div
							className={`add-order-message ${msg.type}`}
							style={{marginBottom: '1rem'}}
						>
							{msg.text}
						</div>
					)}

					{/* USERS TABLE */}
					<div
						className='table-wrapper'
						style={{boxShadow: 'none', padding: 0, margin: 0}}
					>
						<table className='orders-table'>
							<thead>
								<tr>
									<th>ID</th>
									<th>Usuario</th>
									<th>Rol</th>
									<th className='text-center'>Acciones</th>
								</tr>
							</thead>
							<tbody>
								{users.length > 0 ? (
									users.map((u) => (
										<tr key={u.id}>
											<td>{u.id}</td>
											<td>
												<strong>{u.username}</strong>
											</td>
											<td>
												{editingUserId === u.id ? (
													<select
														value={editingRole}
														onChange={(e) => setEditingRole(e.target.value)}
														style={{
															padding: '4px 8px',
															borderRadius: '6px',
															border: '1px solid #b2bec3',
															fontSize: '0.85rem',
														}}
													>
														<option value='GESTOR'>GESTOR</option>
														<option value='ADMIN'>ADMIN</option>
													</select>
												) : (
													<span
														className='badge'
														style={{
															background:
																u.appUserRole === 'ADMIN' || u.appUserRole === 'SUPER_ADMIN'
																	? '#e1bee7'
																	: u.appUserRole === 'GESTOR'
																	? '#fff3e0'
																	: '#e0f2f1',
															color:
																u.appUserRole === 'ADMIN' || u.appUserRole === 'SUPER_ADMIN'
																	? '#7b1fa2'
																	: u.appUserRole === 'GESTOR'
																	? '#e65100'
																	: '#00695c',
															padding: '4px 12px',
															borderRadius: '20px',
															fontSize: '0.85rem',
														}}
													>
														{u.appUserRole}
													</span>
												)}
											</td>
											<td className='text-center' style={{display: 'flex', gap: '6px', justifyContent: 'center'}}>
												{editingUserId === u.id ? (
													<>
														<button
															className='btn-delete'
															style={{color: '#00b894'}}
															onClick={() => handleEditRole(u.id)}
															title='Confirmar'
														>
															<FaCheck />
														</button>
														<button
															className='btn-delete'
															onClick={() => setEditingUserId(null)}
															title='Cancelar'
														>
															<FaTimes />
														</button>
													</>
												) : (
													<>
														<button
															className='btn-delete'
															style={{color: '#0984e3'}}
															onClick={() => {
																setEditingUserId(u.id);
																setEditingRole(u.appUserRole);
															}}
															title='Editar rol'
														>
															<FaEdit />
														</button>
														<button
															className='btn-delete'
															onClick={() => handleDeleteUser(u.id)}
															title='Eliminar usuario'
														>
															<FaTrashAlt />
														</button>
													</>
												)}
											</td>
										</tr>
									))
								) : (
									<tr>
										<td
											colSpan='4'
											className='text-center'
											style={{padding: '2rem'}}
										>
											No se encontraron usuarios.
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</div>
				</div>
			</div>

			{/* MODAL CREAR USUARIO */}
			{showUserModal && (
				<div
					className='modal-overlay'
					onClick={() => setShowUserModal(false)}
				>
					<div
						className='modal-content'
						onClick={(e) => e.stopPropagation()}
					>
						<h2 style={{marginBottom: '1.5rem'}}>
							Crear Nuevo Usuario
						</h2>
						<form
							onSubmit={handleCreateUser}
							className='form-input'
						>
							<div className='form-group'>
								<label>Nombre de Usuario</label>
								<input
									type='text'
									value={newUser.username}
									onChange={(e) =>
										setNewUser({
											...newUser,
											username: e.target.value,
										})
									}
									required
									autoFocus
								/>
							</div>
							<div className='form-group'>
								<label>Contraseña</label>
								<input
									type='password'
									value={newUser.password}
									onChange={(e) =>
										setNewUser({
											...newUser,
											password: e.target.value,
										})
									}
									required
								/>
							</div>
							<div className='form-group'>
								<label>Rol / Permisos</label>
								<select
									value={newUser.appUserRole}
									onChange={(e) =>
										setNewUser({
											...newUser,
											appUserRole: e.target.value,
										})
									}
									style={{
										width: '100%',
										padding: '0.8rem',
										borderRadius: '6px',
										border: '1px solid #333',
										background: '#222',
										color: 'white',
									}}
								>
									<option value='GESTOR'>
										Gestor (Facturas y cobranzas)
									</option>
									<option value='ADMIN'>
										Administrador (Todo)
									</option>
								</select>
							</div>

							<button
								type='submit'
								className='button_3 margin-5'
								style={{width: '100%', marginTop: '1rem'}}
							>
								Confirmar Creación
							</button>
						</form>
					</div>
				</div>
			)}
		</div>
	);
}

export default AdminPage;
