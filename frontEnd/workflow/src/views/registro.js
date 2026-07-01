import React, {useState} from 'react';
import axios from 'axios';
import {useNavigate} from 'react-router-dom';
import {BASE_URL} from '../api/config';

const Register = () => {
	const navigate = useNavigate();
	const [userData, setUserData] = useState({
		username: '',
		password: '',
		appUserRole: 'GESTOR',
	});
	const [responseMessage, setResponseMessage] = useState('');

	const handleChange = (e) => {
		const {name, value} = e.target;
		setUserData({...userData, [name]: name === 'username' ? value.toLowerCase() : value});
	};

	const handleSubmit = async (e) => {
		e.preventDefault();

		try {
			await axios.post(`${BASE_URL}/api/users/registro`, {
				username: userData.username,
				password: userData.password,
				appUserRole: userData.appUserRole,
			});
			navigate('/login');
		} catch (error) {
			setResponseMessage('Error registering user.');
			console.error(error);
		}
	};

	return (
		<div className='container product-wrapper box-shadow background_amarillo'>
			<h1 className='main-title w-100'>Registro</h1>
			<form onSubmit={handleSubmit}>
				<div className='form-input'>
					<label className='main-label' htmlFor='username'>
						Usuario
					</label>
					<input
						id='username'
						type='text'
						name='username'
						value={userData.username}
						onChange={handleChange}
						required
					/>
				</div>

				<div className='form-input'>
					<label className='main-label' htmlFor='password'>
						Contrasena
					</label>
					<input
						id='password'
						type='password'
						name='password'
						value={userData.password}
						onChange={handleChange}
						required
					/>
				</div>

				<div className='form-input'>
					<label className='main-label' htmlFor='appUserRole'>
						Rol
					</label>
					<select
						id='appUserRole'
						name='appUserRole'
						value={userData.appUserRole}
						onChange={handleChange}
						required
					>
						<option value='GESTOR'>GESTOR</option>
						<option value='ADMIN'>ADMIN</option>
					</select>
				</div>

				<button type='submit'>Enviar</button>
			</form>
			<p>{responseMessage}</p>
		</div>
	);
};

export default Register;
