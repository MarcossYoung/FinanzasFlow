import React, {useState, useContext} from 'react';
import {useNavigate} from 'react-router-dom';
import axios from 'axios';
import {UserContext} from '../UserProvider';
import {BASE_URL} from '../api/config';

const Login = () => {
	const navigate = useNavigate();
	const {setUser} = useContext(UserContext);
	const [formData, setFormData] = useState({username: '', password: ''});
	const [error, setError] = useState(null);

	const handleChange = (e) => {
		setFormData({...formData, [e.target.name]: e.target.value});
	};

	const handleSubmit = async (e) => {
		e.preventDefault();
		setError(null);
		localStorage.removeItem('user');
		localStorage.removeItem('token');
		setUser(null);
		try {
			const payload = {
				username: formData.username.trim().toLowerCase(),
				password: formData.password,
			};
			const response = await axios.post(
				`${BASE_URL}/api/users/login`,
				payload
			);
			if (response.status === 200) {
				const userData = response.data;
				localStorage.setItem('user', JSON.stringify(userData));
				localStorage.setItem('token', userData.token);
				setUser(userData);
				navigate('/');
			}
		} catch (error) {
			localStorage.removeItem('user');
			localStorage.removeItem('token');
			setUser(null);
			if (error.response?.status === 401) {
				setError('Usuario o contraseña inválida. Intenta de nuevo.');
			} else {
				setError('Ocurrió un error, por favor prueba otra vez.');
			}
		}
	};


	return (
		<div className='login-wrapper'>
			<div className='login-card box-shadow'>
				<h1 className='main-title text-center'>Iniciar Sesión</h1>

				{error && <div className='error-message'>{error}</div>}

				<form onSubmit={handleSubmit} className='form-input'>
					<label htmlFor='username'>Usuario</label>
					<input
						type='text'
						id='username'
						name='username'
						placeholder='Ingresa tu usuario'
						value={formData.username}
						onChange={handleChange}
						required
					/>
					<small className='input-hint'>El usuario no distingue mayúsculas</small>

					<label htmlFor='password'>Contraseña</label>
					<input
						type='password'
						id='password'
						name='password'
						placeholder='••••••••'
						value={formData.password}
						onChange={handleChange}
						required
					/>

					<button type='submit' className='button_3 w-100 margin-5'>
						Entrar
					</button>
				</form>
			</div>
		</div>
	);
};

export default Login;
