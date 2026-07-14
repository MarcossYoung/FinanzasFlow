import React, {useState, useEffect, useContext, useCallback} from 'react';
import axios from 'axios';
import {FaChevronLeft, FaChevronRight} from 'react-icons/fa';
import {UserContext} from '../UserProvider';
import {BASE_URL} from '../api/config';
import StatCard from '../components/statCard';
import ExpensePieChart from '../components/expensesPieChart';
import LedgerAttachInput from '../components/LedgerAttachInput';

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
	const [showManualForm, setShowManualForm] = useState(false);

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

	const handleExtracted = (extraction) => {
		setFormData((prev) => ({
			...prev,
			date: extraction.issueDate || prev.date,
			amount: extraction.amount ?? prev.amount,
			reason: extraction.titulo || extraction.description || prev.reason,
		}));
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
		<div className='fit-view costs-page'>

			{/* Header */}
			<div className='page-header costs-header-row'>
				<h2 className='page-title'>Panel de Costos</h2>
				<div className='costs-controls'>
					<select
						value={selectedType}
						onChange={(e) => {setSelectedType(e.target.value); setCurrentPage(0);}}
						className='filter-input'
					>
						<option value='ALL'>Todos los tipos</option>
						{COST_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
					</select>
					<input
						type='month'
						value={selectedMonth}
						onChange={(e) => {setSelectedMonth(e.target.value); setCurrentPage(0);}}
						className='filter-input'
					/>
				</div>
			</div>

			{/* KPI Cards */}
			<div className='kpi-grid costs-kpi-row'>
				<StatCard title='Total del Período' value={formatMoney(summary?.total)} borderColor='#e17055' />
				<StatCard title='Registros' value={costs.length > 0 ? `${costs.length} gastos` : '-'} borderColor='#636e72' />
				{topCategory && (
					<StatCard
						title={`Mayor: ${typeLabel(topCategory.costType)}`}
						value={formatMoney(topCategory.total)}
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

			{/* Reporting chart + manual correction disclosure */}
			<div className='costs-main-grid'>

				<div className='panel'>
					<h3 className='card-section-title'>Distribución por Tipo</h3>
					{pieData.length > 0
						? <ExpensePieChart data={pieData} />
						: <p className='empty-state-text'>Sin datos para el período.</p>
					}
				</div>

				<div className='panel manual-entry-panel'>
					<button
						type='button'
						className='button-green manual-entry-disclosure'
						onClick={() => setShowManualForm((visible) => !visible)}
						aria-expanded={showManualForm}
					>
						Agregar
					</button>
					{showManualForm && <form onSubmit={handleSubmit} className='costs-form manual-entry-form'>
						<LedgerAttachInput onExtracted={handleExtracted} />
						<div className='costs-form-row'>
							<div className='input-group'>
								<label>Fecha</label>
								<input type='date' value={formData.date}
									onChange={(e) => setFormData({...formData, date: e.target.value})}
									required />
							</div>
							<div className='input-group'>
								<label>Monto ($)</label>
								<input type='number' placeholder='0.00' value={formData.amount}
									onChange={(e) => setFormData({...formData, amount: e.target.value})}
									required />
							</div>
						</div>
						<div className='input-group'>
							<label>Asunto</label>
							<input type='text' placeholder='Ej: Pago de alquiler depósito' value={formData.reason}
								onChange={(e) => setFormData({...formData, reason: e.target.value})}
								required />
						</div>
						<div className='costs-form-row'>
							<div className='input-group'>
								<label>Tipo</label>
								<select value={formData.costType}
									onChange={(e) => setFormData({...formData, costType: e.target.value})}>
									{COST_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
								</select>
							</div>
							<div className='input-group'>
								<label>Frecuencia</label>
								<select value={formData.frequency}
									onChange={(e) => setFormData({...formData, frequency: e.target.value})}>
									<option value='ONE_TIME'>Única vez</option>
									<option value='WEEKLY'>Semanal</option>
									<option value='MONTHLY'>Mensual</option>
									<option value='YEARLY'>Anual</option>
								</select>
							</div>
						</div>
						<button type='submit' className='button-green'>
							Agregar Gasto
						</button>
					</form>}
				</div>
			</div>

			{/* Costs Table */}
			<div className='panel costs-table-panel'>
				<div className='costs-table-header'>
					<h3 className='card-section-title'>Gastos del Período</h3>
				</div>
				<table className='orders-table mobile-card-table'>
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
							<tr><td colSpan='6' className='empty-cell'>Cargando...</td></tr>
						) : costs.length > 0 ? (
							costs.map((c) =>
								editingId === c.id ? (
									<tr key={c.id} className='table-edit-row'>
										<td data-label='Fecha'><input type='date' value={editForm.date}
											onChange={(e) => setEditForm({...editForm, date: e.target.value})}
											className='table-edit-input' /></td>
										<td data-label='Asunto'><input type='text' value={editForm.reason}
											onChange={(e) => setEditForm({...editForm, reason: e.target.value})}
											className='table-edit-input' /></td>
										<td data-label='Tipo'>
											<select value={editForm.costType}
												onChange={(e) => setEditForm({...editForm, costType: e.target.value})}>
												{COST_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
											</select>
										</td>
										<td data-label='Monto'><input type='number' value={editForm.amount}
											onChange={(e) => setEditForm({...editForm, amount: e.target.value})}
											className='table-edit-input' /></td>
										<td data-label='Frecuencia'>
											<select value={editForm.frequency}
												onChange={(e) => setEditForm({...editForm, frequency: e.target.value})}>
												<option value='ONE_TIME'>Única vez</option>
												<option value='WEEKLY'>Semanal</option>
												<option value='MONTHLY'>Mensual</option>
												<option value='YEARLY'>Anual</option>
											</select>
										</td>
										<td className='text-center' data-label='Acciones'>
											<div className='table-actions centered'>
												<button className='button-green' onClick={() => saveEdit(c.id)}>Guardar</button>
												<button className='btn-delete' onClick={() => setEditingId(null)}>Cancelar</button>
											</div>
										</td>
									</tr>
								) : (
									<tr key={c.id}>
										<td data-label='Fecha'>{c.date}</td>
										<td data-label='Asunto'>
											{c.reason || '-'}
											{c.reason?.endsWith('(Auto)') && <span className='auto-recurring-icon'>🔁</span>}
										</td>
										<td data-label='Tipo'><span className='cost-type-badge'>{typeLabel(c.costType)}</span></td>
										<td className='amount-cell' data-label='Monto'>{formatMoney(c.amount)}</td>
										<td data-label='Frecuencia'>
											{c.frequency !== 'ONE_TIME' ? (
												<span className='freq-badge'>
													{freqLabel(c.frequency)}
												</span>
											) : 'Única vez'}
										</td>
										<td className='text-center' data-label='Acciones'>
											<div className='table-actions centered'>
												<button className='btn-pill'
													onClick={() => startEdit(c)}>Editar</button>
												<button className='btn-delete' onClick={() => handleDelete(c.id)}>Eliminar</button>
											</div>
										</td>
									</tr>
								)
							)
						) : (
							<tr><td colSpan='6' className='empty-cell'>
								No hay gastos para el período seleccionado.
							</td></tr>
						)}
					</tbody>
				</table>

				<div className='pagination-controls'>
					<button disabled={currentPage === 0} onClick={() => setCurrentPage((p) => p - 1)}
						className='btn-pagination'>
						<FaChevronLeft /> Anterior
					</button>
					<span className='pagination-current'>
						Página {currentPage + 1} de {totalPages || 1}
					</span>
					<button disabled={currentPage >= totalPages - 1} onClick={() => setCurrentPage((p) => p + 1)}
						className='btn-pagination'>
						Siguiente <FaChevronRight />
					</button>
				</div>
			</div>
		</div>
	);
}
