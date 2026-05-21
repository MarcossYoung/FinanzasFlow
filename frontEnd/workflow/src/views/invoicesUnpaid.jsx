import InvoicesTable from '../components/ordersTable';

export default function InvoicesUnpaid() {
	return (
		<div className='p-6'>
			<h1 className='main-title'>Sin cobrar</h1>
			<InvoicesTable endpoint='/api/invoices/not-picked-up' />
		</div>
	);
}
