import React from 'react';
import './css/styles.css';

const Loader = () => (
	<div className='loader-layout'>
		<div className='loader-spinner' aria-hidden='true'></div>
		<h3 className='loader-title'>Cargando FinanzasFlow...</h3>
	</div>
);

export default Loader;
