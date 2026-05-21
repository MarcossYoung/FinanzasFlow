// Add this helper component to your Finance.jsx file
export default function StatCard({title, value, icon, borderColor}) {
	return (
		<div
			className='card'
			style={{
				background: '#ffffff',
				padding: '20px',
				borderRadius: '10px',
				borderLeft: `6px solid ${borderColor}`,
				boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
				display: 'flex',
				alignItems: 'center',
				gap: '15px',
			}}
		>
			<span style={{fontSize: '2rem'}}>{icon}</span>
			<div>
				<h4
					style={{
						margin: 0,
						color: '#636e72',
						fontSize: '0.8rem',
						textTransform: 'uppercase',
					}}
				>
					{title}
				</h4>
				<p
					style={{
						margin: '5px 0 0',
						color: '#2d3436',
						fontSize: '1.4rem',
						fontWeight: 'bold',
					}}
				>
					{value}
				</p>
			</div>
		</div>
	);
}
