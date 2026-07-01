import InvoicesTable from '../components/ordersTable';

export default function InvoicesDueSoon() {
	return (
		<div className='p-6'>
			<h1 className='main-title'>Por vencer esta semana</h1>
			<InvoicesTable endpoint='/api/invoices/due-this-week' />
		</div>
	);
}
