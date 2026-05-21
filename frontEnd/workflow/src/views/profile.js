import React, {useEffect, useContext} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import axios from 'axios';
import {UserContext} from '../UserProvider';
import {FaUserCircle, FaSignOutAlt, FaTrashAlt} from 'react-icons/fa';
import {BASE_URL} from '../api/config';

const UserProfile = () => {
	const {id} = useParams();
	const {user, setUser} = useContext(UserContext);
	const navigate = useNavigate();
	const isAdmin = user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN';
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
		try {
			await axios.delete(`${BASE_URL}/api/users/${user.id}`);
			localStorage.removeItem('user');
			navigate('/login');
		} catch (error) {
			console.error('Error deleting user:', error);
		}
	};

	if (!user) return <p className='text-center'>Cargando perfil...</p>;

	return (
		<div className='profile-wrapper'>
			<div className='profile-card box-shadow'>
				<div className='profile-header'>
					<FaUserCircle className='profile-avatar' />
					<div>
						<h2 className='username'>{user.username}</h2>
						<p className='userid'>ID: {user.id}</p>
						<p className='role-badge'>
							{isAdmin ? 'Administrador' : 'Gestor'}
						</p>
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
