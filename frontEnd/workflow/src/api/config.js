const LOCAL_URL = 'http://localhost:8080';
const PRODUCTION_URL = 'https://finanzasflow-production.up.railway.app';

export const BASE_URL =
	process.env.REACT_APP_API_URL ||
	(process.env.NODE_ENV === 'production'
		? process.env.REACT_APP_PROD_URL || PRODUCTION_URL
		: process.env.REACT_APP_TEST_URL || LOCAL_URL);
