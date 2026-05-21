import InvoicesTable from '../components/ordersTable';

export default function InvoicesAll() {
	return (
		<div className='p-6'>
			<h1 className='main-title'>Todas las facturas</h1>
			<InvoicesTable endpoint='/api/invoices' />
		</div>
	);
}
