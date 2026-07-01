import React, {useEffect, useState, useCallback} from 'react';
import axios from 'axios';
import {
	FaUser,
	FaClipboardList,
	FaClock,
	FaCheckCircle,
	FaTrashAlt,
	FaEdit,
	FaCheck,
	FaTimes,
	FaKey,
	FaTelegramPlane,
	FaCopy,
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
	const [msg, setMsg] = useState({text: '', type: ''}); // Unified message state
	const [editingUserId, setEditingUserId] = useState(null);
	const [editingRole, setEditingRole] = useState('');
	const [passwordUser, setPasswordUser] = useState(null);
	const [newPassword, setNewPassword] = useState('');
	const [telegramConnections, setTelegramConnections] = useState([]);
	const [telegramOwnerId, setTelegramOwnerId] = useState('');
	const [connectCode, setConnectCode] = useState(null);
	const currentUser = JSON.parse(localStorage.getItem('user') || 'null');
	const canChangeSuperadminPassword = currentUser?.role === 'SUPER_ADMIN';

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
			const fetchedUsers = usersRes.data;
			setUsers(fetchedUsers);
			if (!telegramOwnerId && fetchedUsers.length > 0) {
				setTelegramOwnerId(String(fetchedUsers[0].id));
			}

			const telegramRes = await axios.get(`${BASE_URL}/api/admin/telegram/connections`, config);
			setTelegramConnections(telegramRes.data);
		} catch (err) {
			console.error('Error fetching admin data:', err);
		}
	}, [telegramOwnerId]);

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

	const handleChangePassword = async (e) => {
		e.preventDefault();
		if (!passwordUser) return;
		if (newPassword.length < 8) {
			setMsg({text: 'La contraseña debe tener al menos 8 caracteres', type: 'red'});
			return;
		}
		try {
			const token = localStorage.getItem('token');
			await axios.put(
				`${BASE_URL}/api/admin/users/${passwordUser.id}/password`,
				{password: newPassword},
				{headers: {Authorization: `Bearer ${token}`}},
			);
			setMsg({text: 'Contraseña actualizada', type: 'green'});
			setPasswordUser(null);
			setNewPassword('');
			setTimeout(() => setMsg({text: '', type: ''}), 3000);
		} catch {
			setMsg({text: 'No se pudo actualizar la contraseña', type: 'red'});
		}
	};

	const handleGenerateTelegramCode = async () => {
		if (!telegramOwnerId) {
			setMsg({text: 'Elegi un responsable para Telegram', type: 'red'});
			return;
		}
		try {
			const token = localStorage.getItem('token');
			const res = await axios.post(
				`${BASE_URL}/api/admin/telegram/connect-codes`,
				{defaultOwnerId: Number(telegramOwnerId)},
				{headers: {Authorization: `Bearer ${token}`}},
			);
			setConnectCode(res.data);
		} catch {
			setMsg({text: 'No se pudo generar el codigo de Telegram', type: 'red'});
		}
	};

	const handleDisableTelegramConnection = async (id) => {
		if (!window.confirm('Deshabilitar esta conexion de Telegram?')) return;
		try {
			const token = localStorage.getItem('token');
			await axios.delete(`${BASE_URL}/api/admin/telegram/connections/${id}`, {
				headers: {Authorization: `Bearer ${token}`},
			});
			fetchData();
		} catch {
			setMsg({text: 'No se pudo deshabilitar la conexion', type: 'red'});
		}
	};

	const handleUpdateTelegramOwner = async (connectionId, ownerId) => {
		try {
			const token = localStorage.getItem('token');
			await axios.put(
				`${BASE_URL}/api/admin/telegram/connections/${connectionId}/owner`,
				{defaultOwnerId: Number(ownerId)},
				{headers: {Authorization: `Bearer ${token}`}},
			);
			setMsg({text: 'Responsable de Telegram actualizado', type: 'green'});
			fetchData();
			setTimeout(() => setMsg({text: '', type: ''}), 3000);
		} catch {
			setMsg({text: 'No se pudo actualizar el responsable', type: 'red'});
		}
	};

	const copyConnectCode = () => {
		if (!connectCode?.code || !navigator.clipboard) return;
		navigator.clipboard.writeText(`/connect ${connectCode.code}`);
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
				<div className='admin-digest'>
					<div>
						<strong className='admin-digest-label'>Resumen Semanal IA</strong>
						<p className='admin-digest-text'>{digest}</p>
					</div>
					<button
						onClick={dismissDigest}
						className='admin-digest-close'
						title='Cerrar'
					>
						×
					</button>
				</div>
			)}

			{/* 1. HEADER & KPI CARDS */}
			<div className='dashboard-header admin-dashboard-header'>
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
			<div className='admin-main-stack'>
				{/* SECTION A: CHART */}
				<div className='costs-wrapper admin-section-panel admin-chart-panel'>
					<h2 className='admin-section-title'>Resumen de Actividad</h2>
					<div className='admin-chart-container'>
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
								<Tooltip />
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

				{/* SECTION B: TELEGRAM */}
				<div className='costs-wrapper admin-section-panel'>
					<div className='admin-users-header'>
						<h2>Telegram</h2>
						<div className='admin-telegram-actions'>
							<select
								value={telegramOwnerId}
								onChange={(e) => setTelegramOwnerId(e.target.value)}
								className='admin-role-select'
							>
								{users.map((u) => (
									<option key={u.id} value={u.id}>
										{u.username}
									</option>
								))}
							</select>
							<button
								className='button_3'
								onClick={handleGenerateTelegramCode}
							>
								<FaTelegramPlane /> Generar codigo
							</button>
						</div>
					</div>

					<div className='table-wrapper admin-users-table'>
						<table className='orders-table mobile-card-table'>
							<thead>
								<tr>
									<th>Chat</th>
									<th>Tipo</th>
									<th>Responsable</th>
									<th>Conectado</th>
									<th className='text-center'>Acciones</th>
								</tr>
							</thead>
							<tbody>
								{telegramConnections.length > 0 ? (
									telegramConnections.map((conn) => (
										<tr key={conn.id}>
											<td data-label='Chat'>
												<strong>{conn.chatTitle || conn.chatId}</strong>
												<div className='admin-muted-text'>{conn.chatId}</div>
											</td>
											<td data-label='Tipo'>{conn.chatType || 'private'}</td>
											<td data-label='Responsable'>
												<select
													value={conn.defaultOwnerId || ''}
													onChange={(e) => handleUpdateTelegramOwner(conn.id, e.target.value)}
													className='admin-role-select'
												>
													{users.map((u) => (
														<option key={u.id} value={u.id}>
															{u.username}
														</option>
													))}
												</select>
											</td>
											<td data-label='Conectado'>{conn.createdAt ? new Date(conn.createdAt).toLocaleString() : '-'}</td>
											<td data-label='Acciones' className='text-center admin-user-actions'>
												<button
													className='btn-delete'
													onClick={() => handleDisableTelegramConnection(conn.id)}
													title='Deshabilitar'
												>
													<FaTrashAlt />
												</button>
											</td>
										</tr>
									))
								) : (
									<tr>
										<td
											colSpan='5'
											className='text-center admin-empty-cell'
										>
											No hay chats conectados.
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</div>
				</div>

				{/* SECTION C: USER MANAGEMENT */}
				<div className='costs-wrapper admin-section-panel admin-users-section'>
					<div
						className='admin-users-header'
					>
						<h2>Gestión de Usuarios</h2>
					</div>

					{/* Feedback Messages */}
					{msg.text && (
						<div
							className={`add-order-message admin-message ${msg.type}`}
						>
							{msg.text}
						</div>
					)}

					{/* USERS TABLE */}
					<div
						className='table-wrapper admin-users-table'
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
														className='admin-role-select'
													>
														<option value='GESTOR'>GESTOR</option>
														<option value='ADMIN'>ADMIN</option>
													</select>
												) : (
													<span
														className={`admin-role-badge admin-role-${u.appUserRole.toLowerCase().replace('_', '-')}`}
													>
														{u.appUserRole}
													</span>
												)}
											</td>
											<td className='text-center admin-user-actions'>
												{editingUserId === u.id ? (
													<>
														<button
															className='btn-delete admin-action-confirm'
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
															className='btn-delete admin-action-edit'
															onClick={() => {
																setEditingUserId(u.id);
																setEditingRole(u.appUserRole);
															}}
															title='Editar rol'
														>
															<FaEdit />
														</button>
														{canChangeSuperadminPassword && u.appUserRole === 'SUPER_ADMIN' && (
															<button
																className='btn-delete admin-action-password'
																onClick={() => {
																	setPasswordUser(u);
																	setNewPassword('');
																}}
																title='Cambiar contraseña'
															>
																<FaKey />
															</button>
														)}
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
											className='text-center admin-empty-cell'
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

			{/* MODAL CAMBIAR CONTRASEÑA SUPERADMIN */}
			{passwordUser && (
				<div
					className='modal-overlay'
					onClick={() => setPasswordUser(null)}
				>
					<div
						className='modal-content'
						onClick={(e) => e.stopPropagation()}
					>
						<h2 className='admin-modal-title'>
							Cambiar Contraseña
						</h2>
						<form
							onSubmit={handleChangePassword}
							className='form-input'
						>
							<div className='form-group'>
								<label>Usuario</label>
								<input
									type='text'
									value={passwordUser.username}
									disabled
								/>
							</div>
							<div className='form-group'>
								<label>Nueva Contraseña</label>
								<input
									type='password'
									value={newPassword}
									onChange={(e) => setNewPassword(e.target.value)}
									minLength='8'
									required
									autoFocus
								/>
							</div>
							<button
								type='submit'
								className='button_3 margin-5 admin-modal-submit'
							>
								Actualizar Contraseña
							</button>
						</form>
					</div>
				</div>
			)}

			{/* MODAL CODIGO TELEGRAM */}
			{connectCode && (
				<div
					className='modal-overlay'
					onClick={() => setConnectCode(null)}
				>
					<div
						className='modal-content'
						onClick={(e) => e.stopPropagation()}
					>
						<h2 className='admin-modal-title'>Codigo Telegram</h2>
						<div className='admin-connect-code'>{connectCode.code}</div>
						<p className='admin-muted-text'>
							Envia /connect {connectCode.code} al bot desde un chat privado.
						</p>
						<button
							type='button'
							className='button_3 margin-5 admin-modal-submit'
							onClick={copyConnectCode}
						>
							<FaCopy /> Copiar comando
						</button>
					</div>
				</div>
			)}

		</div>
	);
}

export default AdminPage;
