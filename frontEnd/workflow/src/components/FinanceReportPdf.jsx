import React from 'react';
import {Document, Page, Text, View, Image, StyleSheet} from '@react-pdf/renderer';

const styles = StyleSheet.create({
	page: {padding: 40, fontFamily: 'Helvetica', backgroundColor: '#ffffff'},
	header: {
		flexDirection: 'row',
		justifyContent: 'space-between',
		alignItems: 'flex-start',
		marginBottom: 24,
		borderBottom: '2px solid #6c5ce7',
		paddingBottom: 12,
	},
	brand: {fontSize: 20, fontWeight: 'bold', color: '#6c5ce7'},
	reportTitle: {fontSize: 13, color: '#2d3436', marginTop: 4},
	period: {fontSize: 10, color: '#636e72', marginTop: 2},
	metaRight: {alignItems: 'flex-end'},
	metaLabel: {fontSize: 8, color: '#b2bec3'},
	metaValue: {fontSize: 10, color: '#2d3436', fontWeight: 'bold'},
	kpiGrid: {flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginBottom: 24},
	kpiCard: {
		width: '18%',
		backgroundColor: '#f8f9fa',
		borderRadius: 6,
		padding: 10,
		borderLeft: '3px solid #6c5ce7',
	},
	kpiLabel: {fontSize: 8, color: '#636e72', marginBottom: 4},
	kpiValue: {fontSize: 12, fontWeight: 'bold', color: '#2d3436'},
	sectionTitle: {
		fontSize: 11,
		fontWeight: 'bold',
		color: '#2d3436',
		marginBottom: 8,
		marginTop: 16,
	},
	aiText: {
		fontSize: 10,
		color: '#636e72',
		lineHeight: 1.6,
		backgroundColor: '#f8f9fa',
		padding: 12,
		borderRadius: 6,
		borderLeft: '3px solid #6c5ce7',
	},
	chartImage: {width: '100%', marginTop: 8, borderRadius: 6},
	footer: {
		position: 'absolute',
		bottom: 30,
		left: 40,
		right: 40,
		borderTop: '1px solid #dfe6e9',
		paddingTop: 8,
		flexDirection: 'row',
		justifyContent: 'space-between',
	},
	footerText: {fontSize: 8, color: '#b2bec3'},
});

const formatMoney = (value) =>
	'$' + Number(value || 0).toLocaleString('es-AR', {maximumFractionDigits: 0});

const formatMonth = (value) => {
	if (!value) return '';
	const [year, month] = value.split('-');
	return new Date(Number(year), Number(month) - 1, 1).toLocaleDateString(
		'es-AR',
		{month: 'long', year: 'numeric'},
	);
};

export default function FinanceReportPdf({
	financeData,
	barChartImg,
	pieChartImg,
	aiInsight,
	selectedMonth,
	userName,
}) {
	const kpis = [
		{label: 'Ingresos Totales', value: formatMoney(financeData.tInc), color: '#00b894'},
		{label: 'Efectivo Recibido', value: formatMoney(financeData.tDep), color: '#0984e3'},
		{label: 'Pendiente de Cobro', value: formatMoney(financeData.pendienteCobro), color: '#e17055'},
		{label: 'Gastos Operativos', value: formatMoney(financeData.tExp), color: '#ff7675'},
		{label: 'Ganancia Neta', value: formatMoney(financeData.netProfit), color: '#6c5ce7'},
	];

	return (
		<Document>
			<Page size='A4' style={styles.page}>
				<View style={styles.header}>
					<View>
						<Text style={styles.brand}>FinanzasFlow</Text>
						<Text style={styles.reportTitle}>Reporte Financiero</Text>
						<Text style={styles.period}>Periodo: {formatMonth(selectedMonth)}</Text>
					</View>
					<View style={styles.metaRight}>
						<Text style={styles.metaLabel}>GENERADO</Text>
						<Text style={styles.metaValue}>{new Date().toLocaleDateString('es-AR')}</Text>
						<Text style={[styles.metaLabel, {marginTop: 6}]}>USUARIO</Text>
						<Text style={styles.metaValue}>{userName || '-'}</Text>
					</View>
				</View>

				<View style={styles.kpiGrid}>
					{kpis.map(({label, value, color}) => (
						<View key={label} style={[styles.kpiCard, {borderLeftColor: color}]}>
							<Text style={styles.kpiLabel}>{label}</Text>
							<Text style={styles.kpiValue}>{value}</Text>
						</View>
					))}
				</View>

				<Text style={styles.sectionTitle}>Analisis IA</Text>
				<Text style={styles.aiText}>
					{aiInsight || 'No se genero analisis IA para este periodo.'}
				</Text>

				{barChartImg && (
					<>
						<Text style={styles.sectionTitle}>Rendimiento por Cliente</Text>
						<Image src={barChartImg} style={styles.chartImage} />
					</>
				)}
				{pieChartImg && (
					<>
						<Text style={styles.sectionTitle}>Distribucion de Gastos</Text>
						<Image src={pieChartImg} style={styles.chartImage} />
					</>
				)}

				<View style={styles.footer} fixed>
					<Text style={styles.footerText}>
						FinanzasFlow - Reporte generado automaticamente
					</Text>
					<Text style={styles.footerText}>{formatMonth(selectedMonth)}</Text>
				</View>
			</Page>
		</Document>
	);
}
