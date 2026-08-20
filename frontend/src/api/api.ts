import axios from 'axios';

// Create an Axios instance
const api = axios.create({
  // Use relative path in production (Nginx proxy) and absolute path in development
  baseURL: import.meta.env.DEV ? 'http://localhost:8080/api' : '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add a request interceptor to attach the JWT token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token && token !== 'null' && token !== 'undefined') {
      config.headers['Authorization'] = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

export default api;
