const PRODUCTION_URL = 'https://finanzasflow-production.up.railway.app';

export const BASE_URL =
  process.env.REACT_APP_API_URL || PRODUCTION_URL;
