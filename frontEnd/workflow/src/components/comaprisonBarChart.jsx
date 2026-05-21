export default function ComparisonBarChart({data, height = 300}) {
	const maxIncome = Math.max(1, ...data.map((d) => Number(d.income)));
	const maxQty = Math.max(1, ...data.map((d) => Number(d.itemCount)));

	return (
		<svg
			viewBox={`0 0 1000 ${height}`}
			style={{
				width: '100%',
				height: 'auto',
				backgroundColor: '#fff',
				borderRadius: '8px',
			}}
		>
			{/* Baseline */}
			<line
				x1='50'
				y1={height - 40}
				x2='950'
				y2={height - 40}
				stroke='#dfe6e9'
				strokeWidth='2'
			/>

			{data.map((d, i) => {
				const groupW = 900 / data.length;
				const barW = groupW * 0.3;
				const xBase = 50 + i * groupW;

				// Scaling bars to their respective maximums
				const hIncome = ((height - 80) * d.income) / maxIncome;
				const hQty = ((height - 80) * d.itemCount) / maxQty;

				return (
					<g key={i}>
						{/* Units Sold Bar (Blue/Purple) */}
						<rect
							x={xBase + 5}
							y={height - 40 - hQty}
							width={barW}
							height={hQty}
							fill='#a29bfe'
							rx='4'
						/>
						<text
							x={xBase + 5 + barW / 2}
							y={height - 45 - hQty}
							fontSize='12'
							textAnchor='middle'
							fill='#6c5ce7'
							fontWeight='bold'
						>
							{d.itemCount}
						</text>

						{/* Income Bar (Green) */}
						<rect
							x={xBase + barW + 12}
							y={height - 40 - hIncome}
							width={barW}
							height={hIncome}
							fill='#55efc4'
							rx='4'
						/>
						<text
							x={xBase + barW + 12 + barW / 2}
							y={height - 45 - hIncome}
							fontSize='10'
							textAnchor='middle'
							fill='#00b894'
						>
							${(d.income / 1000).toFixed(1)}k
						</text>

						{/* User Label (Owner Name) */}
						<text
							x={xBase + groupW / 2}
							y={height - 15}
							fontSize='14'
							textAnchor='middle'
							fill='#2d3436'
							fontWeight='500'
						>
							{d.label}
						</text>
					</g>
				);
			})}
		</svg>
	);
}
