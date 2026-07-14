import React, {useEffect, useContext} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import axios from 'axios';
import {UserContext} from '../UserProvider';
import {FaSignOutAlt, FaTrashAlt} from 'react-icons/fa';
import {BASE_URL} from '../api/config';

const UserProfile = () => {
	const {id} = useParams();
	const {user, setUser} = useContext(UserContext);
	const navigate = useNavigate();
	const userId = id || user?.id;

	useEffect(() => {
		const storedUser = JSON.parse(localStorage.getItem('user'));
		if (storedUser) {
			setUser(storedUser);
		} else if (userId) {
			axios
				.get(`${BASE_URL}/api/users/${userId}`)
				.then((response) => setUser(response.data))
				.catch((error) => console.error('Error fetching user:', error));
		}
	}, [userId, setUser]);

	const handleLogOut = () => {
		localStorage.removeItem('user');
		setUser(null);
		navigate('/login');
	};

	const handleDeleteUser = async () => {
		if (!window.confirm('¿Eliminar tu cuenta? Esta acción es irreversible.')) return;
		try {
			await axios.delete(`${BASE_URL}/api/users/${user.id}`);
			localStorage.removeItem('user');
			navigate('/login');
		} catch (error) {
			console.error('Error deleting user:', error);
		}
	};

	if (!user) return <p className='text-center'>Cargando perfil...</p>;

	const initials = (user.username || '?').slice(0, 2).toUpperCase();
	const roleClass = user.role === 'SUPER_ADMIN'
		? 'role-super-admin'
		: user.role === 'ADMIN'
			? 'role-admin'
			: 'role-gestor';
	const roleLabel = user.role === 'SUPER_ADMIN'
		? 'Super Admin'
		: user.role === 'ADMIN'
			? 'Administrador'
			: 'Gestor';

	return (
		<div className='profile-wrapper'>
			<div className='profile-card box-shadow'>

				<div className='profile-hero'>
					<div className='profile-avatar-circle'>{initials}</div>
					<h2 className='username'>{user.username}</h2>
					<span className={`role-badge ${roleClass}`}>{roleLabel}</span>
					<small className='userid'>ID: {user.id}</small>
				</div>

				<div className='profile-info-section'>
					<div className='profile-info-row'>
						<span className='profile-info-label'>Rol</span>
						<span className='profile-info-value'>{roleLabel}</span>
					</div>
				</div>

				<div className='profile-actions'>
					<button className='button-green' onClick={handleLogOut}>
						<FaSignOutAlt /> Cerrar Sesión
					</button>
					<button className='button-red' onClick={handleDeleteUser}>
						<FaTrashAlt /> Eliminar Cuenta
					</button>
				</div>

			</div>
		</div>
	);
};

export default UserProfile;
