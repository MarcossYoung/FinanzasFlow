
import React, {useContext, Suspense, lazy} from 'react';
import {
    BrowserRouter as Router,
    Route,
    Routes,
    Navigate,
    useLocation,
} from 'react-router-dom';
import {UserContext} from './UserProvider';
import Loader from './loader';

// Keep these static because they are wrappers/layouts used immediately
import Sidebar from './components/sidebar';
import MobileNav from './components/mobileNav';
import ProtectedRoute from './components/protectedRoute';
import RoleRoute, {defaultRouteFor} from './RoleRoute';
import './css/styles.css';

// --- LAZY LOADED VIEWS ---
const Login = lazy(() => import('./views/login'));
const Dashboard = lazy(() => import('./views/dashboard'));
const Invoices = lazy(() => import('./views/invoicesAll'));
const InvoiceDetail = lazy(() => import('./views/invoiceDetail'));
const InvoiceEdit = lazy(() => import('./views/invoiceEditForm'));
const Profile = lazy(() => import('./views/profile'));
const AdminPage = lazy(() => import('./views/adminPage'));
const OperatorPage = lazy(() => import('./views/OperatorPage'));
const InvoicesUnpaid = lazy(() => import('./views/invoicesUnpaid'));
const Finances = lazy(() => import('./views/finances'));
const CostsManager = lazy(() => import('./views/CostsManager'));
const Customers = lazy(() => import('./views/customers'));
const PrivacyPolicy = lazy(() => import('./views/PrivacyPolicy'));

function AuthenticatedMobileNav() {
    const {user} = useContext(UserContext);
    const location = useLocation();
    const publicRoutes = ['/login', '/privacy-policy'];

    if (!user || publicRoutes.includes(location.pathname)) return null;
    return <MobileNav />;
}

function App() {
    const {user, setUser} = useContext(UserContext);

    return (
        <Router>
            <AuthenticatedMobileNav />
                <Suspense fallback={<Loader />}>
                    <Routes>
                        {/* Public Routes */}
                        <Route path='/privacy-policy' element={<PrivacyPolicy />} />
                        <Route path='/login' element={<Login setUser={setUser} />} />

                        {/* --- PROTECTED DASHBOARD LAYOUT --- */}
                        <Route
                            path='/dashboard'
                            element={
                                <ProtectedRoute user={user}>
                                    <Sidebar />
                                    <Dashboard user={user} />
                                </ProtectedRoute>
                            }
                        >
                            <Route
                                index
                                element={
                                    <RoleRoute
                                        user={user}
                                        allowedRoles={['GESTOR', 'ADMIN']}
                                    >
                                        <Invoices />
                                    </RoleRoute>
                                }
                            />
                            <Route
                                path='no-cobradas'
                                element={
                                    <RoleRoute
                                        user={user}
                                        allowedRoles={['GESTOR', 'ADMIN']}
                                    >
                                        <InvoicesUnpaid />
                                    </RoleRoute>
                                }
                            />
                        </Route>

                        {/* --- OTHER PROTECTED PAGES --- */}
                        <Route
                            path='/finance'
                            element={
                                <RoleRoute
                                    user={user}
                                    allowedRoles={['ADMIN', 'GESTOR']}
                                >
                                    <Sidebar />
                                    <div className='main-content'>
                                        <Finances />
                                    </div>
                                </RoleRoute>
                            }
                        />
                        <Route
                            path='/customers'
                            element={
                                <RoleRoute
                                    user={user}
                                    allowedRoles={['ADMIN', 'GESTOR']}
                                >
                                    <Sidebar />
                                    <div className='main-content'>
                                        <Customers />
                                    </div>
                                </RoleRoute>
                            }
                        />
                        <Route
                            path='/costs'
                            element={
                                <RoleRoute
                                    user={user}
                                    allowedRoles={['ADMIN']}
                                >
                                    <Sidebar />
                                    <div className='main-content'>
                                        <CostsManager />
                                    </div>
                                </RoleRoute>
                            }
                        />
                        <Route
                            path='/profile/:id'
                            element={
                                <ProtectedRoute user={user}>
                                    <Sidebar />
                                    <Profile user={user} />
                                </ProtectedRoute>
                            }
                        />
                        <Route
                            path='/invoices/:invoiceId'
                            element={
                                <ProtectedRoute user={user}>
                                    <Sidebar />
                                    <InvoiceDetail />
                                </ProtectedRoute>
                            }
                        />
                        <Route
                            path='/invoices/:invoiceId/edit'
                            element={
                                <RoleRoute
                                    user={user}
                                    allowedRoles={['ADMIN', 'GESTOR']}
                                >
                                    <Sidebar />
                                    <InvoiceEdit />
                                </RoleRoute>
                            }
                        />
                        <Route
                            path='/admin'
                            element={
                                <RoleRoute
                                    user={user}
                                    allowedRoles={['ADMIN']}
                                >
                                    <Sidebar />
                                    <AdminPage />
                                </RoleRoute>
                            }
                        />
                        <Route
                            path='/operator'
                            element={
                                <RoleRoute
                                    user={user}
                                    allowedRoles={['SUPER_ADMIN']}
                                >
                                    <Sidebar />
                                    <div className='main-content'>
                                        <OperatorPage />
                                    </div>
                                </RoleRoute>
                            }
                        />

                        {/* Redirects */}
                        <Route path='/' element={<Navigate to={defaultRouteFor(user)} replace />} />
                        <Route path='*' element={<Navigate to={defaultRouteFor(user)} replace />} />
                    </Routes>
                </Suspense>
        </Router>
    );
}

export default App;
