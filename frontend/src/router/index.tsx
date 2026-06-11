import { createBrowserRouter } from 'react-router-dom';
import App from '../App';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
    children: [
      {
        index: true,
        element: <div>Dashboard</div>,
      },
      {
        path: 'profiles',
        element: <div>Profiles</div>,
      },
      {
        path: 'posts',
        element: <div>Posts</div>,
      },
      {
        path: 'analytics',
        element: <div>Analytics</div>,
      },
      {
        path: 'notifications',
        element: <div>Notifications</div>,
      },
    ],
  },
  {
    path: '/login',
    element: <div>Login</div>,
  },
]);
