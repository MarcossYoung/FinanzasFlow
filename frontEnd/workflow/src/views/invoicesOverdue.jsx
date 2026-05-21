import InvoicesTable from '../components/ordersTable';

export default function InvoicesOverdue() {
	return (
		<div className='p-6'>
			<h1 className='main-title'>Vencidas</h1>
			<InvoicesTable endpoint='/api/invoices/past-due' />
		</div>
	);
}
