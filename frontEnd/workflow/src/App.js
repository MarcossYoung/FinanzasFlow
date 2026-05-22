
import React, {useContext, Suspense, lazy} from 'react';
import {
    BrowserRouter as Router,
    Route,
    Routes,
    Navigate,
} from 'react-router-dom';
import {UserContext} from './UserProvider';
import {InvoicesProvider} from './InvoicesContext';
import Loader from './loader';

// Keep these static because they are wrappers/layouts used immediately
import Sidebar from './components/sidebar';
import ProtectedRoute from './components/protectedRoute';
import RoleRoute from './RoleRoute';
import AiChatPanel from './components/AiChatPanel';
import './css/styles.css';

// --- LAZY LOADED VIEWS ---
const Login = lazy(() => import('./views/login'));
const Register = lazy(() => import('./views/registro'));
const Dashboard = lazy(() => import('./views/dashboard'));
const Invoices = lazy(() => import('./views/invoicesAll'));
const InvoiceDetail = lazy(() => import('./views/invoiceDetail'));
const InvoiceEdit = lazy(() => import('./views/invoiceEditForm'));
const Profile = lazy(() => import('./views/profile'));
const AdminPage = lazy(() => import('./views/adminPage'));
const OperatorPage = lazy(() => import('./views/OperatorPage'));
const InvoicesDueSoon = lazy(() => import('./views/invoicesDueSoon'));
const InvoicesUnpaid = lazy(() => import('./views/invoicesUnpaid'));
const InvoicesOverdue = lazy(() => import('./views/invoicesOverdue'));
const Finances = lazy(() => import('./views/finances'));
const CostsManager = lazy(() => import('./views/CostsManager'));
const Customers = lazy(() => import('./views/customers'));
const PrivacyPolicy = lazy(() => import('./views/PrivacyPolicy'));

function App() {
    const {user, setUser} = useContext(UserContext);

    return (
        <InvoicesProvider>
            <Router>
                <AiChatPanel />
                <Suspense fallback={<Loader />}>
                    <Routes>
                        {/* Public Routes */}
                        <Route path='/privacy-policy' element={<PrivacyPolicy />} />
                        <Route path='/login' element={<Login setUser={setUser} />} />
                        <Route path='/registro' element={<Register />} />

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
                                path='por-vencer'
                                element={
                                    <RoleRoute
                                        user={user}
                                        allowedRoles={['GESTOR', 'ADMIN']}
                                    >
                                        <InvoicesDueSoon user={user} />
                                    </RoleRoute>
                                }
                            />
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
                            <Route
                                path='vencidas'
                                element={
                                    <RoleRoute
                                        user={user}
                                        allowedRoles={['GESTOR', 'ADMIN']}
                                    >
                                        <InvoicesOverdue />
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
                        <Route path='/' element={<Navigate to='/dashboard' replace />} />
                        <Route path='/products/:productId' element={<Navigate to='/dashboard' replace />} />
                        <Route path='/inventory' element={<Navigate to='/dashboard' replace />} />
                        <Route path='*' element={<Navigate to='/dashboard' replace />} />
                    </Routes>
                </Suspense>
            </Router>
        </InvoicesProvider>
    );
}

export default App;
