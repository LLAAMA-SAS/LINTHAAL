import Head from 'next/head';
import Layout, { siteTitle } from '../components/layout';
import utilStyles from '../styles/utils.module.css';
import Link from 'next/link';

export default function Home() {
  return (
    <Layout home>
      <Head>
      </Head>
      <section className={utilStyles.headingMd}>
        <p>
        Linthaal is a Graph-of-thoughts multi-agent library for Computational Biology.{' '}
        </p>
        <p>
        <Link href="/tot_pubmed/summarizationRequests">Check Previous Summarizations</Link>
        </p>
        <p>
          Or create new query:
        </p>
        <div>
          <form onSubmit={handleSubmit}>
            <div><label>search:<input type="text" id="search_term" /></label></div>
            <div><label>title length: <input type="number" id="title_length" /></label></div>
            <div><label>abstract length: <input type="number" id="title_length" /></label></div>
            <div><label>update: <input type="number" id="title_length" /></label></div>
            <div><label>max abstracts: <input type="number" id="title_length" /></label></div>
            <div><label>choose model: <input type="text" id="title_length" /></label></div>
            <div><button type="submit" onSubmit={handleSubmit}>Submit</button></div>
          </form>
        </div>
      </section>
    </Layout>
  );
}


async function handleSubmit() {
  e.preventDefault();

  try {
    const response = await fetch('/your-backend-api-endpoint', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ query }),
    });

    if (!response.ok) {
      throw new Error('Network response was not ok');
    }

    const data = await response.json();
    setResult(data.result); // Assuming your backend responds with a 'result' field
  } catch (error) {
    console.error('Error:', error);
    // Handle error
  }
};