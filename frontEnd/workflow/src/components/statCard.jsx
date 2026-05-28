// StatCard — icon is optional. When omitted the label+value fill the full card width.
export default function StatCard({title, value, icon, borderColor}) {
	return (
		<div
			className='card stat-card'
			style={{borderLeft: `5px solid ${borderColor}`}}
		>
			{icon && <span className='stat-card-icon'>{icon}</span>}
			<div className='stat-card-body'>
				<span className='stat-card-label'>{title}</span>
				<strong className='stat-card-value'>{value}</strong>
			</div>
		</div>
	);
}
