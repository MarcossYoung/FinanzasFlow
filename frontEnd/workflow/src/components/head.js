import React from 'react';
import {Helmet} from 'react-helmet';

const Head = () => {
	return (
		<Helmet>
			{/* Meta Tags */}
			<meta charSet='UTF-8' />
			<meta httpEquiv='X-UA-Compatible' content='IE=edge' />
			<meta
				name='viewport'
				content='width=device-width, initial-scale=1.0'
			/>

			{/* Preconnect for Fonts */}
			<link rel='preconnect' href='https://fonts.googleapis.com' />
			<link
				rel='preconnect'
				href='https://fonts.gstatic.com'
				crossOrigin='true'
			/>

			{/* Google Fonts */}
			<link
				href='https://fonts.googleapis.com/css2?family=Roboto&display=swap'
				rel='stylesheet'
			/>

			{/* Favicon */}
			<link rel='icon' href='/img/favicon.png' type='image/x-icon' />

			{/* Stylesheets */}
			<link rel='stylesheet' href='/css/styles.css' />

			{/* Font Awesome */}
			<link
				rel='stylesheet'
				href='https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css'
			/>

			{/* Custom Stylesheets */}
			<link rel='stylesheet' href='/css/styles.css' />
			<link
				rel='stylesheet'
				href='/css/styles-sm.css'
				media='(min-width:481px)'
			/>
			<link
				rel='stylesheet'
				href='/css/styles-md.css'
				media='(min-width:769px)'
			/>
		</Helmet>
	);
};

export default Head;
