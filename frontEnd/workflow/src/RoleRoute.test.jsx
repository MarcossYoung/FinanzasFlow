import {render, screen} from '@testing-library/react';
import RoleRoute, {defaultRouteFor} from './RoleRoute';

jest.mock('react-router-dom', () => ({
	Navigate: ({to}) => <span>redirect:{to}</span>,
}), {virtual: true});

test.each([
	[{role: 'SUPER_ADMIN'}, '/operator'],
	[{role: 'ADMIN'}, '/finance'],
	[{role: 'GESTOR'}, '/finance'],
	[null, '/login'],
])('selects the role-specific default route', (user, expected) => {
	expect(defaultRouteFor(user)).toBe(expected);
});

test('does not grant SUPER_ADMIN access to ADMIN routes', () => {
	render(
		<RoleRoute user={{role: 'SUPER_ADMIN'}} allowedRoles={['ADMIN']}><span>costs</span></RoleRoute>,
	);
	expect(screen.getByText('redirect:/operator')).toBeInTheDocument();
	expect(screen.queryByText('costs')).not.toBeInTheDocument();
});
