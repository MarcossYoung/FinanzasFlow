import React, {useState, useEffect, useContext, useCallback} from 'react';
import axios from 'axios';
import {FaChevronLeft, FaChevronRight} from 'react-icons/fa';
import {UserContext} from '../UserProvider';
import {BASE_URL} from '../api/config';
import StatCard from '../components/statCard';
import ExpensePieChart from '../components/expensesPieChart';

const COST_TYPES = [
	{value: 'RENT', label: 'Alquiler'},
	{value: 'MATERIAL', label: 'Materiales'},
	{value: 'SALARY', label: 'Salarios'},
	{value: 'TAX', label: 'Impuestos'},
	{value: 'ADS', label: 'Anuncios'},
	{value: 'SERVICES', label: 'Servicios'},
	{value: 'OTHERS', label: 'Otros'},
];

const typeLabel = (v) => COST_TYPES.find((t) => t.value === v)?.label ?? v;

const freqLabel = (f) => {
	if (f === 'WEEKLY') return 'Semanal';
	if (f === 'MONTHLY') return 'Mensual';
	if (f === 'YEARLY') return 'Anual';
	return 'Única vez';
};

const formatMoney = (v) =>
	Number(v || 0).toLocaleString('es-AR', {style: 'currency', currency: 'ARS', maximumFractionDigits: 0});

export default function CostsManager() {
	const {user} = useContext(UserContext);

	const [costs, setCosts] = useState([]);
	const [loading, setLoading] = useState(true);
	const [currentPage, setCurrentPage] = useState(0);
	const [totalPages, setTotalPages] = useState(0);
	const [summary, setSummary] = useState(null);

	const [selectedMonth, setSelectedMonth] = useState(() => {
		const now = new Date();
		return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
	});
	const [selectedType, setSelectedType] = useState('ALL');

	const [aiInsight, setAiInsight] = useState('');
	const [aiLoading, setAiLoading] = useState(false);

	const [editingId, setEditingId] = useState(null);
	const [editForm, setEditForm] = useState({});

	const [formData, setFormData] = useState({
		date: new Date().toISOString().split('T')[0],
		amount: '',
		costType: 'OTHERS',
		frequency: 'ONE_TIME',
		reason: '',
	});

	const monthRange = useCallback(() => {
		const [year, month] = selectedMonth.split('-').map(Number);
		return {
			from: `${selectedMonth}-01`,
			to: new Date(year, month, 0).toISOString().split('T')[0],
		};
	}, [selectedMonth]);

	const fetchCosts = useCallback(async () => {
		try {
			setLoading(true);
			const {from, to} = monthRange();
			const params = {page: currentPage, size: 10, from, to};
			if (selectedType !== 'ALL') params.costType = selectedType;

			const [costsRes, summaryRes] = await Promise.all([
				axios.get(`${BASE_URL}/api/costs`, {
					headers: {Authorization: `Bearer ${user.token}`},
					params,
				}),
				axios.get(`${BASE_URL}/api/costs/summary`, {
					headers: {Authorization: `Bearer ${user.token}`},
					params: {from, to},
				}),
			]);

			setCosts(costsRes.data.content ?? costsRes.data);
			setTotalPages(costsRes.data.totalPages ?? 1);
			setSummary(summaryRes.data);
		} catch (err) {
			console.error('Error fetching costs', err);
		} finally {
			setLoading(false);
		}
	}, [user.token, currentPage, selectedType, monthRange]);

	useEffect(() => {
		fetchCosts();
	}, [fetchCosts]);

	const handleAnalyze = async () => {
		setAiLoading(true);
		setAiInsight('');
		try {
			const {from, to} = monthRange();
			const res = await axios.post(
				`${BASE_URL}/api/ai/finance-insight`,
				{from, to},
				{headers: {Authorization: `Bearer ${user.token}`}},
			);
			setAiInsight(res.data.insight);
		} catch {
			setAiInsight('Error al generar análisis.');
		} finally {
			setAiLoading(false);
		}
	};

	const handleSubmit = async (e) => {
		e.preventDefault();
		try {
			await axios.post(`${BASE_URL}/api/costs`, formData, {
				headers: {Authorization: `Bearer ${user.token}`},
			});
			setFormData({...formData, amount: '', reason: ''});
			fetchCosts();
		} catch {
			alert('Error al guardar gasto');
		}
	};

	const handleDelete = async (id) => {
		if (!window.confirm('¿Eliminar este gasto?')) return;
		try {
			await axios.delete(`${BASE_URL}/api/costs/${id}`, {
				headers: {Authorization: `Bearer ${user.token}`},
			});
			fetchCosts();
		} catch (err) {
			console.error(err);
		}
	};

	const startEdit = (c) => {
		setEditingId(c.id);
		setEditForm({date: c.date, amount: c.amount, costType: c.costType, frequency: c.frequency, reason: c.reason});
	};

	const saveEdit = async (id) => {
		try {
			await axios.put(`${BASE_URL}/api/costs/${id}`, editForm, {
				headers: {Authorization: `Bearer ${user.token}`},
			});
			setEditingId(null);
			fetchCosts();
		} catch {
			alert('Error al guardar');
		}
	};

	const pieData = summary?.breakdown
		?.filter((b) => Number(b.total) > 0)
		?.map((b) => ({name: typeLabel(b.costType), value: Number(b.total)})) ?? [];

	const topCategory = summary?.breakdown?.reduce(
		(top, b) => (Number(b.total) > Number(top?.total ?? 0) ? b : top),
		null,
	);

	return (
		<div className='fit-view costs-page' style={{padding: 0, backgroundColor: 'var(--color-bg)', minHeight: 0}}>

			{/* Header */}
			<div className='costs-header-row' style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 0}}>
				<h2 style={{margin: 0, color: '#2d3436'}}>Panel de Costos</h2>
				<div style={{display: 'flex', gap: 12, alignItems: 'center'}}>
					<select
						value={selectedType}
						onChange={(e) => {setSelectedType(e.target.value); setCurrentPage(0);}}
						style={{padding: '10px', borderRadius: '8px', border: '1px solid #dfe6e9', boxShadow: '0 2px 4px rgba(0,0,0,0.05)'}}
					>
						<option value='ALL'>Todos los tipos</option>
						{COST_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
					</select>
					<input
						type='month'
						value={selectedMonth}
						onChange={(e) => {setSelectedMonth(e.target.value); setCurrentPage(0);}}
						style={{padding: '10px', borderRadius: '8px', border: '1px solid #dfe6e9', boxShadow: '0 2px 4px rgba(0,0,0,0.05)'}}
					/>
				</div>
			</div>

			{/* KPI Cards */}
			<div className='costs-kpi-row' style={{display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '12px', marginBottom: 0}}>
				<StatCard title='Total del Período' value={formatMoney(summary?.total)} icon='💸' borderColor='#e17055' />
				<StatCard title='Registros' value={costs.length > 0 ? `${costs.length} gastos` : '-'} icon='📋' borderColor='#636e72' />
				{topCategory && (
					<StatCard
						title={`Mayor: ${typeLabel(topCategory.costType)}`}
						value={formatMoney(topCategory.total)}
						icon='📌'
						borderColor='#fdcb6e'
					/>
				)}
			</div>

			{/* AI Insight */}
			<div className='ai-insight-card'>
				<div className='ai-insight-card-header'>
					<h3 className='ai-insight-card-title'>Análisis IA</h3>
					<button
						className='ai-analyze-btn'
						onClick={handleAnalyze}
						disabled={aiLoading}
					>
						{aiLoading ? 'Analizando...' : 'Analizar'}
					</button>
				</div>
				{aiInsight ? (
					<p className='ai-insight-text'>{aiInsight}</p>
				) : (
					<p className='ai-insight-placeholder'>
						Haz clic en "Analizar" para obtener un resumen de los costos del período.
					</p>
				)}
			</div>

			{/* Pie Chart + Add Form */}
			<div className='costs-main-grid' style={{display: 'grid', gridTemplateColumns: '1fr 1.5fr', gap: '16px', marginBottom: 0}}>

				<div className='panel'>
					<h3 className='card-section-title'>Distribución por Tipo</h3>
					{pieData.length > 0
						? <ExpensePieChart data={pieData} />
						: <p style={{color: '#b2bec3', textAlign: 'center', paddingTop: '2rem'}}>Sin datos para el período.</p>
					}
				</div>

				<div className='panel'>
					<h3 className='card-section-title'>Registrar Nuevo Gasto</h3>
					<form onSubmit={handleSubmit} style={{display: 'flex', flexDirection: 'column', gap: 14}}>
						<div style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12}}>
							<div>
								<label style={{fontSize: 12, color: '#636e72', display: 'block', marginBottom: 4}}>Fecha</label>
								<input type='date' value={formData.date}
									onChange={(e) => setFormData({...formData, date: e.target.value})}
									required style={{width: '100%', padding: '8px', borderRadius: 8, border: '1px solid #dfe6e9'}} />
							</div>
							<div>
								<label style={{fontSize: 12, color: '#636e72', display: 'block', marginBottom: 4}}>Monto ($)</label>
								<input type='number' placeholder='0.00' value={formData.amount}
									onChange={(e) => setFormData({...formData, amount: e.target.value})}
									required style={{width: '100%', padding: '8px', borderRadius: 8, border: '1px solid #dfe6e9'}} />
							</div>
						</div>
						<div>
							<label style={{fontSize: 12, color: '#636e72', display: 'block', marginBottom: 4}}>Asunto</label>
							<input type='text' placeholder='Ej: Pago de alquiler depósito' value={formData.reason}
								onChange={(e) => setFormData({...formData, reason: e.target.value})}
								required style={{width: '100%', padding: '8px', borderRadius: 8, border: '1px solid #dfe6e9'}} />
						</div>
						<div style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12}}>
							<div>
								<label style={{fontSize: 12, color: '#636e72', display: 'block', marginBottom: 4}}>Tipo</label>
								<select value={formData.costType}
									onChange={(e) => setFormData({...formData, costType: e.target.value})}
									style={{width: '100%', padding: '8px', borderRadius: 8, border: '1px solid #dfe6e9'}}>
									{COST_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
								</select>
							</div>
							<div>
								<label style={{fontSize: 12, color: '#636e72', display: 'block', marginBottom: 4}}>Frecuencia</label>
								<select value={formData.frequency}
									onChange={(e) => setFormData({...formData, frequency: e.target.value})}
									style={{width: '100%', padding: '8px', borderRadius: 8, border: '1px solid #dfe6e9'}}>
									<option value='ONE_TIME'>Única vez</option>
									<option value='WEEKLY'>Semanal</option>
									<option value='MONTHLY'>Mensual</option>
									<option value='YEARLY'>Anual</option>
								</select>
							</div>
						</div>
						<button type='submit' className='button-green' style={{height: 'auto', padding: '10px'}}>
							Agregar Gasto
						</button>
					</form>
				</div>
			</div>

			{/* Costs Table */}
			<div className='panel' style={{padding: 0, overflow: 'hidden', flexShrink: 0}}>
				<div style={{padding: '1.25rem 1.5rem', borderBottom: '1px solid var(--color-border-light)'}}>
					<h3 style={{margin: 0, fontWeight: 700, color: 'var(--color-text)', fontSize: '0.95rem'}}>Gastos del Período</h3>
				</div>
				<table className='orders-table' style={{width: '100%'}}>
					<thead>
						<tr>
							<th>Fecha</th>
							<th>Asunto</th>
							<th>Tipo</th>
							<th>Monto</th>
							<th>Frecuencia</th>
							<th className='text-center'>Acciones</th>
						</tr>
					</thead>
					<tbody>
						{loading ? (
							<tr><td colSpan='6' style={{textAlign: 'center', padding: '2rem'}}>Cargando...</td></tr>
						) : costs.length > 0 ? (
							costs.map((c) =>
								editingId === c.id ? (
									<tr key={c.id} style={{background: '#f8f9fa'}}>
										<td><input type='date' value={editForm.date}
											onChange={(e) => setEditForm({...editForm, date: e.target.value})}
											style={{width: '100%'}} /></td>
										<td><input type='text' value={editForm.reason}
											onChange={(e) => setEditForm({...editForm, reason: e.target.value})}
											style={{width: '100%'}} /></td>
										<td>
											<select value={editForm.costType}
												onChange={(e) => setEditForm({...editForm, costType: e.target.value})}>
												{COST_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
											</select>
										</td>
										<td><input type='number' value={editForm.amount}
											onChange={(e) => setEditForm({...editForm, amount: e.target.value})}
											style={{width: '100%'}} /></td>
										<td>
											<select value={editForm.frequency}
												onChange={(e) => setEditForm({...editForm, frequency: e.target.value})}>
												<option value='ONE_TIME'>Única vez</option>
												<option value='WEEKLY'>Semanal</option>
												<option value='MONTHLY'>Mensual</option>
												<option value='YEARLY'>Anual</option>
											</select>
										</td>
										<td className='text-center'>
											<div style={{display: 'flex', gap: 6, justifyContent: 'center'}}>
												<button className='button-green' onClick={() => saveEdit(c.id)}>Guardar</button>
												<button className='btn-delete' onClick={() => setEditingId(null)}>Cancelar</button>
											</div>
										</td>
									</tr>
								) : (
									<tr key={c.id}>
										<td>{c.date}</td>
										<td>
											{c.reason || '-'}
											{c.reason?.endsWith('(Auto)') && <span style={{marginLeft: 6, fontSize: 12}}>🔁</span>}
										</td>
										<td><span className='badge OTRO'>{typeLabel(c.costType)}</span></td>
										<td style={{fontWeight: 'bold'}}>{formatMoney(c.amount)}</td>
										<td>
											{c.frequency !== 'ONE_TIME' ? (
												<span style={{background: '#dfe6e9', borderRadius: 12, padding: '2px 8px', fontSize: 12}}>
													{freqLabel(c.frequency)}
												</span>
											) : 'Única vez'}
										</td>
										<td className='text-center'>
											<div style={{display: 'flex', gap: 6, justifyContent: 'center'}}>
												<button className='btn-pill' style={{fontSize: 12, padding: '4px 10px'}}
													onClick={() => startEdit(c)}>Editar</button>
												<button className='btn-delete' onClick={() => handleDelete(c.id)}>Eliminar</button>
											</div>
										</td>
									</tr>
								)
							)
						) : (
							<tr><td colSpan='6' className='text-center' style={{padding: '2rem', color: '#888'}}>
								No hay gastos para el período seleccionado.
							</td></tr>
						)}
					</tbody>
				</table>

				<div style={{display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '20px', padding: '20px'}}>
					<button disabled={currentPage === 0} onClick={() => setCurrentPage((p) => p - 1)}
						className='btn-pagination' style={{
							background: 'white', border: '1px solid #ddd', padding: '0.5rem 1rem',
							borderRadius: 8, cursor: currentPage === 0 ? 'not-allowed' : 'pointer',
							opacity: currentPage === 0 ? 0.5 : 1, display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: 600,
						}}>
						<FaChevronLeft /> Anterior
					</button>
					<span style={{fontWeight: 'bold', color: '#555'}}>
						Página {currentPage + 1} de {totalPages || 1}
					</span>
					<button disabled={currentPage >= totalPages - 1} onClick={() => setCurrentPage((p) => p + 1)}
						className='btn-pagination' style={{
							background: 'white', border: '1px solid #ddd', padding: '0.5rem 1rem',
							borderRadius: 8, cursor: currentPage >= totalPages - 1 ? 'not-allowed' : 'pointer',
							opacity: currentPage >= totalPages - 1 ? 0.5 : 1, display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: 600,
						}}>
						Siguiente <FaChevronRight />
					</button>
				</div>
			</div>
		</div>
	);
}
