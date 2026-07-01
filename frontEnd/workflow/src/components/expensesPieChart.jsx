export default function ExpensePieChart({data}) {
	const total = data.reduce((sum, item) => sum + item.value, 0);
	let cumulativePercent = 0;
	const colors = ['#fdcb6e', '#e17055', '#00cec9', '#6c5ce7', '#fab1a0'];

	if (total === 0) return <p>No hay gastos registrados.</p>;

	return (
		<div className='expense-pie-chart'>
			<svg
				viewBox='-1 -1 2 2'
				className='expense-pie-chart-svg'
			>
				{data.map((item, i) => {
					const [startX, startY] = [
						Math.cos(2 * Math.PI * cumulativePercent),
						Math.sin(2 * Math.PI * cumulativePercent),
					];
					cumulativePercent += item.value / total;
					const [endX, endY] = [
						Math.cos(2 * Math.PI * cumulativePercent),
						Math.sin(2 * Math.PI * cumulativePercent),
					];
					const largeArcFlag = item.value / total > 0.5 ? 1 : 0;
					return (
						<path
							key={i}
							d={`M 0 0 L ${startX} ${startY} A 1 1 0 ${largeArcFlag} 1 ${endX} ${endY} Z`}
							fill={colors[i % colors.length]}
						/>
					);
				})}
			</svg>
			<div className='expense-pie-chart-legend'>
				{data.map((item, i) => (
					<div key={i} className='expense-pie-chart-legend-row'>
						<div
							style={{
								width: '12px',
								height: '12px',
								background: colors[i % colors.length],
								marginRight: '10px',
							}}
						/>
						{item.name}:{' '}
						<b>{((item.value / total) * 100).toFixed(0)}%</b>
					</div>
				))}
			</div>
		</div>
	);
}
