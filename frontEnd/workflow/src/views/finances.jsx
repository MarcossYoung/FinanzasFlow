import React, {useState, useEffect, useContext, useRef} from 'react';
import axios from 'axios';
import {pdf} from '@react-pdf/renderer';
import html2canvas from 'html2canvas';
import {UserContext} from '../UserProvider';
import {BASE_URL} from '../api/config';
import StatCard from '../components/statCard';
import ComparisonBarChart from '../components/comaprisonBarChart';
import ExpensePieChart from '../components/expensesPieChart';
import FinanceReportPdf from '../components/FinanceReportPdf';

export default function Finance() {
	const {user} = useContext(UserContext);
	const [financeData, setFinanceData] = useState(null);
	const [loading, setLoading] = useState(true);
	const [aiInsight, setAiInsight] = useState('');
	const [aiLoading, setAiLoading] = useState(false);
	const barChartRef = useRef(null);
	const pieChartRef = useRef(null);

	const isGestor = user?.role === 'GESTOR';

	const handleExportPdf = async () => {
		const [barCanvas, pieCanvas] = await Promise.all([
			barChartRef.current
				? html2canvas(barChartRef.current, {scale: 2})
				: Promise.resolve(null),
			pieChartRef.current
				? html2canvas(pieChartRef.current, {scale: 2})
				: Promise.resolve(null),
		]);
		const barChartImg = barCanvas ? barCanvas.toDataURL('image/png') : null;
		const pieChartImg = pieCanvas ? pieCanvas.toDataURL('image/png') : null;

		const blob = await pdf(
			<FinanceReportPdf
				financeData={financeData}
				barChartImg={barChartImg}
				pieChartImg={pieChartImg}
				aiInsight={aiInsight}
				selectedMonth={selectedMonth}
				userName={user?.username}
			/>,
		).toBlob();

		const url = URL.createObjectURL(blob);
		const a = document.createElement('a');
		a.href = url;
		a.download = `reporte-financiero-${selectedMonth}.pdf`;
		a.click();
		URL.revokeObjectURL(url);
	};

	const handleAnalyze = async () => {
		setAiLoading(true);
		setAiInsight('');
		try {
			const [year, month] = selectedMonth.split('-');
			const from = `${year}-${month}-01`;
			const to = new Date(year, month, 0).toISOString().split('T')[0];
			const res = await axios.post(
				`${BASE_URL}/api/ai/finance-insight`,
				{from, to},
				{headers: {Authorization: `Bearer ${user?.token}`}},
			);
			setAiInsight(res.data.insight);
		} catch {
			setAiInsight('Error al generar análisis. Verifique la configuración de la API.');
		} finally {
			setAiLoading(false);
		}
	};

	// Default to the current month
	const [selectedMonth, setSelectedMonth] = useState(() => {
		const now = new Date();
		return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(
			2,
			'0',
		)}`;
	});

	useEffect(() => {
		const loadDashboard = async () => {
			setLoading(true);
			try {
				// Calculate start and end of month based on selectedMonth (YYYY-MM)
				const [year, month] = selectedMonth.split('-');
				const from = `${year}-${month}-01`;
				const to = new Date(year, month, 0).toISOString().split('T')[0];

				const res = await axios.get(`${BASE_URL}/api/finance`, {
					params: {from, to},
					headers: {Authorization: `Bearer ${user?.token}`},
				});

				setFinanceData(res.data);
			} catch (err) {
				console.error('Error fetching dashboard data:', err);
			} finally {
				setLoading(false);
			}
		};

		if (user?.token) {
			loadDashboard();
		}
	}, [selectedMonth, user?.token]);

	if (loading || !financeData)
		return <div className='loader'>Cargando Datos Financieros...</div>;

	// Gestor view: only their own income stats
	if (isGestor) {
		const myStats = financeData.userStats?.find(
			(s) => (s.label || s.userName) === user?.username,
		) ?? {income: 0, unitsSold: 0};

		return (
			<div className='fit-view finance-page'>
				<div className='finance-header page-header'>
					<h2 className='page-title'>Mis Finanzas</h2>
					<div className='finance-actions'>
						<button className='btn-pill' type='button' onClick={handleExportPdf}>
							Exportar PDF
						</button>
						<input
							type='month'
							value={selectedMonth}
							onChange={(e) => setSelectedMonth(e.target.value)}
							className='filter-input'
						/>
					</div>
				</div>

				<div className='kpi-grid'>
					<StatCard
						title='Mis Ingresos'
						value={myStats.income}
						borderColor='#00b894'
					/>
					<StatCard
						title='Facturas'
						value={myStats.unitsSold}
						borderColor='#0984e3'
					/>
				</div>
			</div>
		);
	}

	// Admin view: full dashboard
	return (
		<div className='fit-view finance-page'>
			{/* 1. Header & Month Selector */}
			<div className='finance-header page-header'>
				<h2 className='page-title'>Panel Financiero</h2>
				<div className='finance-actions'>
					<button className='btn-pill' type='button' onClick={handleExportPdf}>
						Exportar PDF
					</button>
					<input
						type='month'
						value={selectedMonth}
						onChange={(e) => setSelectedMonth(e.target.value)}
						className='filter-input'
					/>
				</div>
			</div>

			{/* 2. KPI Cards — Two-step profit */}
			<div className='finance-kpi-stack'>
				{/* Row 1: Ingresos - COGS = Ganancia Bruta */}
				<div className='kpi-grid finance-kpi-row'>
					<StatCard title='Ingresos Totales' value={financeData.tInc}  borderColor='#00b894' />
					<StatCard title='Efectivo Recibido' value={financeData.tDep}borderColor='#636e72' />
					<StatCard
						title='Pendiente de Cobro'
						value={financeData.pendienteCobro}
						
						borderColor='#e17055'
					/>
				</div>
				{/* Row 2: Ganancia Bruta - Gastos = Ganancia Neta */}
				<div className='kpi-grid'>
					<StatCard title='Gastos Operativos' value={financeData.tExp} borderColor='#ff7675' />
					<StatCard title='Ganancia Neta' value={financeData.netProfit}  borderColor='#6c5ce7' />
				</div>
			</div>

			{/* AI Insight Card */}
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
					Haz clic en "Analizar" para obtener un resumen inteligente del mes.
				</p>
			)}
		</div>

		{/* 3. Charts Section */}
			<div className='finance-chart-grid'>
				{/* User Performance (Bar Chart) */}
				<div ref={barChartRef} className='panel'>
					<h3 className='card-section-title'>Rendimiento por Cliente</h3>
					<ComparisonBarChart data={financeData.customerStats || []} />
				</div>

				{/* Expense Breakdown (Pie Chart) */}
				<div ref={pieChartRef} className='panel'>
					<h3 className='card-section-title'>Distribución de Gastos</h3>
					<ExpensePieChart data={financeData.expenseBreakdown} />
				</div>
			</div>
		</div>
	);
}
